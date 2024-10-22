package com.yumi.read_pack.db;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.yumi.read_pack.domain.po.PayType;
import com.yumi.read_pack.domain.po.RedPackOrderStatus;
import com.yumi.read_pack.domain.po.RedPackRecordStatus;
import com.yumi.read_pack.domain.po.RedPackStatus;
import com.yumi.read_pack.domain.po.RedPackType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisPlusConfig {


    @Bean
    public ConfigurationCustomizer codeDescConfigurationCustomizer() {
        return configuration -> {
            configuration.getTypeHandlerRegistry().register(RedPackType.class, EnumCodeTypeHandler.class);
            configuration.getTypeHandlerRegistry().register(RedPackOrderStatus.class, EnumCodeTypeHandler.class);
            configuration.getTypeHandlerRegistry().register(PayType.class, EnumCodeTypeHandler.class);
            configuration.getTypeHandlerRegistry().register(RedPackRecordStatus.class, EnumCodeTypeHandler.class);
            configuration.getTypeHandlerRegistry().register(RedPackStatus.class, EnumCodeTypeHandler.class);
        };
    }
}
