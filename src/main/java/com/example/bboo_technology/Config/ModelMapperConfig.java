package com.example.bboo_technology.Config;

import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ModelMapper 전역 설정용 Config.
 * - @Bean 으로 등록해 두면 Service 단에서 생성자 주입으로 바로 사용 가능.
 */
@Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper mapper = new ModelMapper();

        // 필요하면 매핑 전략/규칙 여기서 커스터마이징
        // mapper.getConfiguration().setFieldMatchingEnabled(true);

        return mapper;
    }
}
