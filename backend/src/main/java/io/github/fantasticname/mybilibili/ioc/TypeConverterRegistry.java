package io.github.fantasticname.mybilibili.ioc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TypeConverterRegistry {

    private final List<TypeConverter> converters = new ArrayList<>();

    public void register(TypeConverter converter) {
        converters.add(converter);
    }

    public Object convert(String value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        for (TypeConverter converter : converters) {
            if (converter.supports(targetType)) {
                return converter.convert(value, targetType);
            }
        }
        throw new IllegalArgumentException("不支持的类型转换: " + targetType.getName());
    }

    public static TypeConverterRegistry createDefault() {
        TypeConverterRegistry registry = new TypeConverterRegistry();

        registry.register(new TypeConverter() {
            @Override
            public boolean supports(Class<?> targetType) {
                return targetType == String.class;
            }

            @Override
            public Object convert(String value, Class<?> targetType) {
                return value;
            }
        });

        registry.register(new TypeConverter() {
            @Override
            public boolean supports(Class<?> targetType) {
                return targetType == int.class || targetType == Integer.class;
            }

            @Override
            public Object convert(String value, Class<?> targetType) {
                return Integer.parseInt(value);
            }
        });

        registry.register(new TypeConverter() {
            @Override
            public boolean supports(Class<?> targetType) {
                return targetType == long.class || targetType == Long.class;
            }

            @Override
            public Object convert(String value, Class<?> targetType) {
                return Long.parseLong(value);
            }
        });

        registry.register(new TypeConverter() {
            @Override
            public boolean supports(Class<?> targetType) {
                return targetType == boolean.class || targetType == Boolean.class;
            }

            @Override
            public Object convert(String value, Class<?> targetType) {
                return Boolean.parseBoolean(value);
            }
        });

        registry.register(new TypeConverter() {
            @Override
            public boolean supports(Class<?> targetType) {
                return targetType == double.class || targetType == Double.class;
            }

            @Override
            public Object convert(String value, Class<?> targetType) {
                return Double.parseDouble(value);
            }
        });

        registry.register(new TypeConverter() {
            @Override
            public boolean supports(Class<?> targetType) {
                return targetType == float.class || targetType == Float.class;
            }

            @Override
            public Object convert(String value, Class<?> targetType) {
                return Float.parseFloat(value);
            }
        });

        registry.register(new TypeConverter() {
            @Override
            public boolean supports(Class<?> targetType) {
                return targetType == short.class || targetType == Short.class;
            }

            @Override
            public Object convert(String value, Class<?> targetType) {
                return Short.parseShort(value);
            }
        });

        registry.register(new TypeConverter() {
            @Override
            public boolean supports(Class<?> targetType) {
                return targetType == byte.class || targetType == Byte.class;
            }

            @Override
            public Object convert(String value, Class<?> targetType) {
                return Byte.parseByte(value);
            }
        });

        registry.register(new TypeConverter() {
            private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            @Override
            public boolean supports(Class<?> targetType) {
                return targetType == LocalDateTime.class;
            }

            @Override
            public Object convert(String value, Class<?> targetType) {
                return LocalDateTime.parse(value, formatter);
            }
        });

        return registry;
    }
}
