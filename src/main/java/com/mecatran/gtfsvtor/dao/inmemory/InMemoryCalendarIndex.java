package com.mecatran.gtfsvtor.dao.inmemory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.mecatran.gtfsvtor.dao.CalendarIndex;
import com.mecatran.gtfsvtor.dao.IndexedReadOnlyDao;
import com.mecatran.gtfsvtor.model.GtfsCalendar;
import com.mecatran.gtfsvtor.model.GtfsCalendar.Id;
import com.mecatran.gtfsvtor.model.GtfsCalendarDate;
import com.mecatran.gtfsvtor.model.GtfsCalendarDateExceptionType;
import com.mecatran.gtfsvtor.model.GtfsId;
import com.mecatran.gtfsvtor.model.GtfsLogicalDate;

public class InMemoryCalendarIndex implements CalendarIndex {

	private Set<GtfsCalendar.Id> allCalendarIds = new HashSet<>();
	private Map<GtfsCalendar.Id, SortedSet<GtfsLogicalDate>> datesPerCalendar = new HashMap<>();
	private SetMultimap<GtfsLogicalDate, GtfsCalendar.Id> calendarsPerDate = HashMultimap
			.create();
	private SetMultimap<GtfsCalendar.Id, GtfsCalendarDate> effectiveCalendarDates = HashMultimap
			.create();
	private Map<GtfsLogicalDate, AtomicLong> tripCountPerDate = new HashMap<>();
	private Map<List<GtfsCalendar.Id>, OverlappingCalendarInfo> calendarOverlapCache = new HashMap<>();
	private List<GtfsLogicalDate> allDatesSorted;

	protected InMemoryCalendarIndex(IndexedReadOnlyDao dao) {
		/*
		 * Here we do not validate any fields for mandatory values. If not
		 * provided, we just ignore them or assume sane default values (null is
		 * empty or invalid day of the week assumed as not active for example).
		 */
		dao.getCalendars().forEach(calendar -> {
			allCalendarIds.add(calendar.getId());
			if (calendar.getStartDate() == null
					|| calendar.getEndDate() == null) {
				// Ignore incomplete calendar
				return;
			}
			GtfsLogicalDate date = calendar.getStartDate();
			while (date.compareTo(calendar.getEndDate()) <= 0) {
				int dow = date.getDayOfTheWeek();
				boolean active = calendar.isActiveOnDow(dow);
				if (active) {
					SortedSet<GtfsLogicalDate> datesForId = datesPerCalendar
							.get(calendar.getId());
					if (datesForId == null) {
						datesForId = new TreeSet<>();
						datesPerCalendar.put(calendar.getId(), datesForId);
					}
					datesForId.add(date);
				}
				date = date.next();
			}
		});
		/* Handle exceptions */
		dao.getCalendarDates().forEach(calDate -> {
			allCalendarIds.add(calDate.getCalendarId());
			if (calDate.getDate() == null
					|| calDate.getExceptionType() == null) {
				// Ignore incomplete calendar date
				return;
			}
			SortedSet<GtfsLogicalDate> datesForId = datesPerCalendar
					.get(calDate.getCalendarId());
			if (datesForId == null) {
				if (calDate
						.getExceptionType() == GtfsCalendarDateExceptionType.REMOVED) {
					// No action needed, no-op removal
					return;
				}
				datesForId = new TreeSet<>();
				datesPerCalendar.put(calDate.getCalendarId(), datesForId);
			}
			boolean active;
			switch (calDate.getExceptionType()) {
			case ADDED:
				active = datesForId.add(calDate.getDate());
				break;
			case REMOVED:
				active = datesForId.remove(calDate.getDate());
				break;
			default:
				active = false;
			}
			if (active) {
				effectiveCalendarDates.put(calDate.getCalendarId(), calDate);
			}
		});
		/*
		 * Build the reversed indexes: 1) calendar IDs for each date, 2) number
		 * of active trips for each date
		 */
		for (Map.Entry<GtfsCalendar.Id, SortedSet<GtfsLogicalDate>> kv : datesPerCalendar
				.entrySet()) {
			GtfsCalendar.Id calendarId = kv.getKey();
			long tripCountForCalendar = dao.getTripsOfCalendar(calendarId)
					.count();
			for (GtfsLogicalDate date : kv.getValue()) {
				calendarsPerDate.put(date, calendarId);
				tripCountPerDate.computeIfAbsent(date, d -> new AtomicLong(0))
						.addAndGet(tripCountForCalendar);
			}
		}
		/* Build the sorted list of all calendar dates */
		allDatesSorted = calendarsPerDate.asMap().keySet().stream().sorted()
				.collect(Collectors.toList());
	}

	@Override
	public Stream<Id> getAllCalendarIds() {
		return allCalendarIds.stream();
	}

	@Override
	public SortedSet<GtfsLogicalDate> getCalendarApplicableDates(
			GtfsCalendar.Id calendarId) {
		SortedSet<GtfsLogicalDate> dates = datesPerCalendar.get(calendarId);
		if (dates == null)
			return Collections.emptySortedSet();
		return Collections.unmodifiableSortedSet(dates);
	}

	@Override
	public Stream<GtfsCalendar.Id> getCalendarIdsOnDate(GtfsLogicalDate date) {
		Set<GtfsCalendar.Id> ret = calendarsPerDate.get(date);
		return ret == null ? Stream.empty() : ret.stream();
	}

	@Override
	public long getTripCountOnDate(GtfsLogicalDate date) {
		AtomicLong ret = tripCountPerDate.get(date);
		return ret == null ? 0 : ret.get();
	}

	@Override
	public Stream<GtfsLogicalDate> getSortedDates() {
		return allDatesSorted.stream();
	}

	@Override
	public Stream<GtfsCalendarDate> getEffectiveExceptionDates(
			GtfsCalendar.Id calendarId) {
		Set<GtfsCalendarDate> ret = effectiveCalendarDates.get(calendarId);
		return ret == null ? Stream.empty() : ret.stream();
	}

	@Override
	public OverlappingCalendarInfo calendarOverlap(GtfsCalendar.Id calendarId1,
			GtfsCalendar.Id calendarId2) {
		if (calendarId1 == null || calendarId2 == null) {
			// Undefined calendars will never overlap.
			return null;
		}
		// Note: this function works too if calendarId1 = calendarId2
		List<GtfsCalendar.Id> key = new ArrayList<>();
		key.add(calendarId1);
		key.add(calendarId2);
		Collections.sort(key, GtfsId.makeComparator());
		/*
		 * TODO Make this caching optional, as for certain pathological GTFS
		 * this may lead to a rather large cache size.
		 */
		return calendarOverlapCache.computeIfAbsent(key, p -> {
			// Compute the number of dates shared by the two calendars
			SortedSet<GtfsLogicalDate> dates1 = getCalendarApplicableDates(
					calendarId1);
			SortedSet<GtfsLogicalDate> dates2 = getCalendarApplicableDates(
					calendarId2);
			if (dates1.isEmpty() || dates2.isEmpty())
				return null;
			// We can do this trick as we have sorted set at hand
			if (dates1.last().compareTo(dates2.first()) < 0
					|| dates2.last().compareTo(dates1.first()) < 0)
				return null;
			// https://stackoverflow.com/questions/2851938/efficiently-finding-the-intersection-of-a-variable-number-of-sets-of-strings/39902694#39902694
			SortedSet<GtfsLogicalDate> smaller, larger;
			if (dates1.size() < dates2.size()) {
				smaller = dates1;
				larger = dates2;
			} else {
				smaller = dates2;
				larger = dates1;
			}
			List<GtfsLogicalDate> intersection = smaller.stream()
					.filter(larger::contains).collect(Collectors.toList());
			if (intersection.isEmpty())
				return null;
			OverlappingCalendarInfo info = new OverlappingCalendarInfo(
					intersection.size(), intersection.get(0),
					intersection.get(intersection.size() - 1));
			return info;
		});
	}

}
