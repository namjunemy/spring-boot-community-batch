package io.namjune.bootbatch.jobs.inactive;

import io.namjune.bootbatch.domain.User;
import io.namjune.bootbatch.domain.enums.UserStatus;
import io.namjune.bootbatch.jobs.inactive.listener.InactiveJobListener;
import io.namjune.bootbatch.jobs.inactive.listener.InactiveStepListener;
import io.namjune.bootbatch.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.FlowExecutionStatus;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Configuration
public class InactiveUserConfig {

    private static final int CHUNK_SIZE = 15;
    private final EntityManagerFactory entityManagerFactory;

    @Bean
    public Job inactiveUserJob(JobBuilderFactory jobBuilderFactory,
                               InactiveJobListener inactiveJobListener,
                               Flow inactiveFlow) {
        return jobBuilderFactory.get("inactiveUserJob")
            .preventRestart()
            .listener(inactiveJobListener)
            .start(inactiveFlow)
            .end()
            .build();
    }

    @Bean
    public Flow inactiveJobFlow(Step inactiveJobStep) {
        FlowBuilder<Flow> flowBuilder = new FlowBuilder<>("inactiveJobFlow");
        return flowBuilder
            .start(new InactiveJobExecutionDecider())
            .on(FlowExecutionStatus.FAILED.getName()).end()
            .on(FlowExecutionStatus.COMPLETED.getName()).to(inactiveJobStep)
            .end();
    }

    @Bean
    public Step inactiveJobStep(StepBuilderFactory stepBuilderFactory,
                                ListItemReader<User> inactiveUserReader,
                                InactiveStepListener inactiveStepListener) {
        return stepBuilderFactory.get("inactiveUserStep")
            .<User, User>chunk(CHUNK_SIZE)
            .reader(inactiveUserReader)
            .processor(inactiveUserProcessor())
            .writer(inactiveUserWriter())
            .listener(inactiveStepListener)
            .build();
    }

    @Bean
    @StepScope
    public ListItemReader<User> inactiveUserReader(@Value("#{jobParameters[nowDate]}") Date nowDate,
                                                   UserRepository userRepository) {
        LocalDateTime now = LocalDateTime.ofInstant(nowDate.toInstant(), ZoneId.systemDefault());
        List<User> inactiveUsers =
            userRepository.findByUpdatedDateBeforeAndStatusEquals(now.minusYears(1), UserStatus.ACTIVE);
        return new ListItemReader<>(inactiveUsers);

    }

    @Bean(destroyMethod = "")
    @StepScope
    public JpaPagingItemReader<User> inactiveUserJpaReader() {
        JpaPagingItemReader<User> jpaPagingItemReader = new JpaPagingItemReader() {

            @Override
            public int getPage() {
                return 0;
            }
        };

        jpaPagingItemReader.setQueryString("select u from User as u " +
                                               "where u.updatedDate < :updatedDate and u.status = :status");

        Map<String, Object> map = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        map.put("updatedDate", now.minusYears(1));
        map.put("status", UserStatus.ACTIVE);

        jpaPagingItemReader.setParameterValues(map);
        jpaPagingItemReader.setEntityManagerFactory(entityManagerFactory);
        jpaPagingItemReader.setPageSize(CHUNK_SIZE);
        return jpaPagingItemReader;
    }

//    @Bean
//    @StepScope
//    public ListItemReader<User> inactiveUserReader() {
//        List<User> oldUsers =
//            userRepository.findByUpdatedDateBeforeAndStatusEquals(LocalDateTime.now(), UserStatus.ACTIVE);
//        return new ListItemReader<>(oldUsers);
//    }

    public ItemProcessor<User, User> inactiveUserProcessor() {
        return User::setInactive;
    }

    public JpaItemWriter<User> inactiveUserWriter() {
        JpaItemWriter<User> jpaItemWriter = new JpaItemWriter<>();
        jpaItemWriter.setEntityManagerFactory(entityManagerFactory);
        return jpaItemWriter;
    }
//    public ItemWriter<User> inactiveUserWriter() {
//        return userRepository::saveAll;
//    }
}
