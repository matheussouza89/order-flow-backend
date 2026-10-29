package com.matheus.orderFlow.shared.messaging;

import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.AbstractJavaTypeMapper;
import org.springframework.amqp.support.converter.ClassMapper;
import org.springframework.amqp.support.converter.MessageConversionException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class FactTypes implements ClassMapper {
    private static final String TYPE_ID_HEADER = AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME;

    private final Map<Class<?>, String> idByType;
    private final Map<String, Class<?>> typeById;

    public FactTypes(Map<String, Class<?>> types, Map<String, Class<?>> legacyTypes) {
        this.idByType = types.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));

        Map<String, Class<?>> readable = new HashMap<>(legacyTypes);
        readable.putAll(types);
        this.typeById = Map.copyOf(readable);
    }

    public String idOf(Class<?> type) {
        String id = idByType.get(type);

        if (id == null) {
            throw new MessageConversionException(type.getName() + " is not a registered fact");
        }

        return id;
    }

    @Override
    public void fromClass(Class<?> type, MessageProperties properties) {
        properties.setHeader(TYPE_ID_HEADER, idOf(type));
    }

    @Override
    public Class<?> toClass(MessageProperties properties) {
        String id = properties.getHeader(TYPE_ID_HEADER);
        Class<?> type = id == null ? null : typeById.get(id);

        if (type == null) {
            throw new MessageConversionException("Unknown fact type: " + id);
        }

        return type;
    }
}
