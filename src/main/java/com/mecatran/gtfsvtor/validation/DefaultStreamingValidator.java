package com.mecatran.gtfsvtor.validation;

import java.util.List;

import com.mecatran.gtfsvtor.model.GtfsObject;
import com.mecatran.gtfsvtor.validation.impl.CompoundStreamingValidator;
import com.mecatran.gtfsvtor.validation.impl.ValidatorInjector;
import com.mecatran.gtfsvtor.validation.streaming.AgencyStreamingValidator;

public class DefaultStreamingValidator
		implements StreamingValidator<GtfsObject<?>> {

	private CompoundStreamingValidator compound;

	public DefaultStreamingValidator(ValidatorConfig config) {
		@SuppressWarnings("unchecked")
		List<? extends StreamingValidator<GtfsObject<?>>> validators = (List<? extends StreamingValidator<GtfsObject<?>>>) ValidatorInjector
				.scanPackageAndInject(StreamingValidator.class,
						this.getClass().getClassLoader(),
						AgencyStreamingValidator.class.getPackage(), config);
		compound = new CompoundStreamingValidator(validators);
	}

	@Override
	public void validate(GtfsObject<?> object, Context context) {
		compound.validate(object, context);
	}
}
