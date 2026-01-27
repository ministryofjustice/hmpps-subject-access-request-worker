package uk.gov.justice.digital.hmpps.subjectaccessrequestworker.config

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration
class AlertCacheConfiguration(
  @param:Value("\${alert-cache.max-size:10000}") private val maxSize: Long,
  @param:Value("\${alert-cache.ttl:30m}") private val ttl: Duration,
) {

  companion object {
    private val log = LoggerFactory.getLogger(AlertCacheConfiguration::class.java)
  }

  init {
    log.info("initialising AlertCacheConfiguration: maxSize={}, ttl={}", maxSize, ttl)
  }

  @Bean("alertServiceCache")
  fun caffeineCache(): Cache<String, String> = Caffeine.newBuilder()
    .maximumSize(maxSize)
    .expireAfterWrite(ttl)
    .build()
}
