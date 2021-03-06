package com.mecatran.gtfsvtor.loader.schema;

import com.mecatran.gtfsvtor.loader.DataRowConverter;
import com.mecatran.gtfsvtor.loader.DataRowConverter.Requiredness;
import com.mecatran.gtfsvtor.model.GtfsCalendar;
import com.mecatran.gtfsvtor.model.GtfsObject;

@TableDescriptorPolicy(objectClass = GtfsCalendar.class, tableName = GtfsCalendar.TABLE_NAME, mandatory = false, mandatoryColumns = {
		"service_id", "monday", "tuesday", "wednesday", "thursday", "friday",
		"saturday", "sunday", "start_date", "end_date" })
public class GtfsCalendarTableDescriptor implements GtfsTableDescriptor {

	@Override
	public GtfsObject<?> parseAndSave(DataRowConverter erow, Context context) {
		GtfsCalendar.Builder builder = new GtfsCalendar.Builder(
				erow.getString("service_id"));
		builder.withSourceLineNumber(
				context.getSourceContext().getSourceRef().getLineNumber())
				.withDow(erow.getBoolean("monday", Requiredness.MANDATORY),
						erow.getBoolean("tuesday", Requiredness.MANDATORY),
						erow.getBoolean("wednesday", Requiredness.MANDATORY),
						erow.getBoolean("thursday", Requiredness.MANDATORY),
						erow.getBoolean("friday", Requiredness.MANDATORY),
						erow.getBoolean("saturday", Requiredness.MANDATORY),
						erow.getBoolean("sunday", Requiredness.MANDATORY))
				.withStartDate(erow.getLogicalDate("start_date",
						Requiredness.MANDATORY))
				.withEndDate(erow.getLogicalDate("end_date",
						Requiredness.MANDATORY));
		GtfsCalendar calendar = builder.build();
		context.getAppendableDao().addCalendar(calendar,
				context.getSourceContext());
		return calendar;
	}
}
