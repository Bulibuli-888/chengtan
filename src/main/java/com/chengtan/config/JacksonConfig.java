package com.chengtan.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * 统一时间格式，避免实体、VO、MQ 消息出现三种不同的时间字符串。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            builder.serializers(new LocalDateTimeSerializer(DATE_TIME));
            builder.serializers(new LocalDateSerializer(DATE));
            builder.serializers(new LocalTimeSerializer(TIME));
            builder.deserializers(new LocalDateTimeDeserializer(DATE_TIME));
            builder.deserializers(new LocalDateDeserializer(DATE));
            builder.deserializers(new LocalTimeDeserializer(TIME));
        };
    }
}
