package io.onedev.server.buildspecmodel.inputspec.dateinput;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.validation.ValidationException;

import com.google.common.collect.Lists;

import io.onedev.server.buildspecmodel.inputspec.InputSpec;
import io.onedev.server.buildspecmodel.inputspec.dateinput.defaultvalueprovider.DefaultValueProvider;

public class DateInput {

	public static String getPropertyDef(InputSpec inputSpec, Map<String, Integer> indexes, 
			DefaultValueProvider defaultValueProvider) {
		int index = indexes.get(inputSpec.getName());
		StringBuffer buffer = new StringBuffer();
		inputSpec.appendField(buffer, index, "Date");
		inputSpec.appendCommonAnnotations(buffer, index);
		if (!inputSpec.isAllowEmpty())
			buffer.append("    @NotNull(message=\"May not be empty\")\n");
		inputSpec.appendMethods(buffer, index, "Date", null, defaultValueProvider);
		
		return buffer.toString();
	}

	public static Object convertToObject(List<String> strings) {
		try {
			if (strings.size() == 1)
				return new Date(Long.parseLong(strings.iterator().next()));
			else if (strings.size() == 0)
				return null;
			else
				throw new ValidationException("Not eligible for multi-value");
		} catch (IllegalArgumentException e) {
			throw new ValidationException(e.getMessage());
		}
	}

	public static List<String> convertToStrings(Object value) {
		if (value != null)
			return Lists.newArrayList(String.valueOf(((Date)value).getTime()));
		else
			return new ArrayList<>();
	}

}
