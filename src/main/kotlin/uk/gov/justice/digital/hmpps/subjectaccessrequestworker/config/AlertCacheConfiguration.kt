package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class AlertCacheConfiguration {

  @Bean("alertServiceCache")
  fun caffeineCache(): Cache<String, String> = Caffeine.newBuilder()
    .maximumSize(10000)
    .expireAfterWrite(Duration.ofMinutes(30))
    .build()
}
