package com.backend.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "s3")
class S3Properties {
    lateinit var url: String
    lateinit var accessKey: String
    lateinit var secretKey: String
    lateinit var bucket: String
    lateinit var region: String
}