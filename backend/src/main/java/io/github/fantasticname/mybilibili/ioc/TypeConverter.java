package io.github.fantasticname.mybilibili.ioc;

public interface TypeConverter {

    boolean supports(Class<?> targetType);

    Object convert(String value, Class<?> targetType);
}
