package com.mecatran.gtfsvtor.dao.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.mecatran.gtfsvtor.dao.StopTimesDao;
import com.mecatran.gtfsvtor.dao.impl.GtfsIdIndexer.GtfsStopIdIndexer;
import com.mecatran.gtfsvtor.model.GtfsStop;
import com.mecatran.gtfsvtor.model.GtfsStopTime;
import com.mecatran.gtfsvtor.model.GtfsTrip;
import com.mecatran.gtfsvtor.model.GtfsTrip.Id;
import com.mecatran.gtfsvtor.model.GtfsTripAndTimes;
import com.mecatran.gtfsvtor.utils.GenericInterner;

public class PackingUnsortedStopTimesDao implements StopTimesDao {

	public static class DefaultContext
			implements PackedUnsortedStopTimes.Context {
		private GtfsIdIndexer.GtfsStopIdIndexer stopIdIndexer;
		private GenericInterner<PackedUnsortedTimePattern> tDataInterner = new GenericInterner<>(
				true);
		private GenericInterner<PackedUnsortedStopPattern> sDataInterner = new GenericInterner<>(
				true);

		public DefaultContext(GtfsStopIdIndexer stopIdIndexer) {
			this.stopIdIndexer = stopIdIndexer;
		}

		@Override
		public int indexStopId(GtfsStop.Id stopId) {
			return stopIdIndexer.index(stopId);
		}

		@Override
		public GtfsStop.Id getStopIdIndex(int stopIdIndex) {
			return stopIdIndexer.unindex(stopIdIndex);
		}

		@Override
		public PackedUnsortedTimePattern intern(
				PackedUnsortedTimePattern tData) {
			return tDataInterner.intern(tData);
		}

		@Override
		public PackedUnsortedStopPattern intern(
				PackedUnsortedStopPattern sData) {
			return sDataInterner.intern(sData);
		}
	}

	private Map<GtfsTrip.Id, PackedUnsortedStopTimes> stopTimes = new HashMap<>();
	private DefaultContext context;
	private int nStopTimes = 0;
	private boolean verbose = false;
	private boolean closed = false;

	public PackingUnsortedStopTimesDao(
			GtfsIdIndexer.GtfsStopIdIndexer stopIdIndexer) {
		context = new DefaultContext(stopIdIndexer);
	}

	public PackingUnsortedStopTimesDao withVerbose(boolean verbose) {
		this.verbose = verbose;
		return this;
	}

	@Override
	public void addStopTime(GtfsStopTime stopTime) {
		if (closed)
			throw new RuntimeException(
					"Cannot re-open a closed PackingUnsortedStopTimesDao. Implement this if needed.");
		nStopTimes++;
		PackedUnsortedStopTimes st = stopTimes.computeIfAbsent(
				stopTime.getTripId(), tid -> new PackedUnsortedStopTimes());
		st.addStopTime(context, stopTime);
	}

	@Override
	public void close() {
		closeIfNeeded();
	}

	@Override
	public int getStopTimesCount() {
		return nStopTimes;
	}

	@Override
	public GtfsTripAndTimes getStopTimesOfTrip(Id tripId, GtfsTrip trip) {
		closeIfNeeded();
		PackedUnsortedStopTimes pst = stopTimes.get(tripId);
		return new GtfsTripAndTimes(trip, pst == null ? Collections.emptyList()
				: pst.getStopTimes(tripId, context));
	}

	private void closeIfNeeded() {
		if (closed)
			return;
		stopTimes.values().forEach(st -> st.sort(context));
		if (verbose) {
			long nStopTimes = getStopTimesCount();
			long nTrips = stopTimes.size();
			long nTimePatterns = context.tDataInterner.size();
			long nStopPatterns = context.sDataInterner.size();
			long nShapeDist = stopTimes.values().stream()
					.filter(psp -> psp.getShapeDistDataSize() > 0).count();
			long nHeadsigns = stopTimes.values().stream()
					.filter(psp -> psp.getHeadsignDataSize() > 0).count();
			// 2 ints and 4 pointers, assume JVM does not pack 32 bits ints
			long tripBytes = nTrips * (6 * 8);
			long tDataBytes = context.tDataInterner.all()
					.mapToInt(psp -> psp.getTDataSize()).sum();
			long sDataBytes = context.sDataInterner.all()
					.mapToInt(psp -> psp.getSDataSize()).sum();
			long pDataBytes = stopTimes.values().stream()
					.mapToInt(psp -> psp.getShapeDistDataSize()).sum();
			long hDataBytes = stopTimes.values().stream()
					.mapToInt(psp -> psp.getHeadsignDataSize()).sum();
			long totalBytes = tripBytes + tDataBytes + sDataBytes + pDataBytes
					+ hDataBytes;
			System.out.println(
					"-----[ Packing unsorted stop times crude memory stats ]----");
			System.out.println(
					"       What          |    Count   | Total (kB) | Per item  ");
			System.out.println(
					"---------------------+------------+------------+-----------");
			System.out.println(String.format(Locale.US,
					"%20s | %10d | %10d | %10.2f", "Stop times", nStopTimes,
					totalBytes / 1024, totalBytes * 1. / nStopTimes));
			System.out.println(String.format(Locale.US,
					"%20s | %10d | %10d | %10.2f", "Trips", nTrips,
					tripBytes / 1024, tripBytes * 1. / nTrips));
			System.out.println(
					String.format(Locale.US, "%20s | %10d | %10d | %10.2f",
							"Time patterns", nTimePatterns, tDataBytes / 1024,
							tDataBytes * 1. / nTimePatterns));
			System.out.println(
					String.format(Locale.US, "%20s | %10d | %10d | %10.2f",
							"Stop patterns", nStopPatterns, sDataBytes / 1024,
							sDataBytes * 1. / nStopPatterns));
			System.out.println(String.format(Locale.US,
					"%20s | %10d | %10d | %10.2f", "Shape distance", nShapeDist,
					pDataBytes / 1024, pDataBytes * 1. / nShapeDist));
			System.out.println(String.format(Locale.US,
					"%20s | %10d | %10d | %10.2f", "Stop headsigns", nHeadsigns,
					hDataBytes / 1024, hDataBytes * 1. / nHeadsigns));
			System.out.println(
					"---------------------+------------+------------+-----------");
		}
		closed = true;
	}
}
