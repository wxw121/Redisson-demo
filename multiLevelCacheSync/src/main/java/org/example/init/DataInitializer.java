package org.example.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.User;
import org.example.service.UserService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 数据初始化器
 * 在应用启动时生成测试数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserService userService;
    private final Random random = new Random();

    @Override
    public void run(ApplicationArguments args) {
        log.info("Initializing test data...");

        // 检查是否已有数据
        if (userService.count() > 0) {
            log.info("Data already exists, skipping initialization");
            return;
        }

        // 生成测试用户
        List<User> users = generateTestUsers(50);
        userService.saveAll(users);

        log.info("Test data initialized with {} users", users.size());
    }

    /**
     * 生成测试用户
     */
    private List<User> generateTestUsers(int count) {
        List<User> users = new ArrayList<>();
        String[] firstNames = {"John", "Jane", "Michael", "Emily", "David", "Sarah", "Robert", "Lisa", "William", "Mary"};
        String[] lastNames = {"Smith", "Johnson", "Williams", "Jones", "Brown", "Davis", "Miller", "Wilson", "Moore", "Taylor"};

        for (int i = 0; i < count; i++) {
            String firstName = firstNames[random.nextInt(firstNames.length)];
            String lastName = lastNames[random.nextInt(lastNames.length)];
            String name = firstName + " " + lastName;
            String username = (firstName.toLowerCase() + lastName.toLowerCase().charAt(0) + random.nextInt(100)).replace(" ", "");
            String email = username + "@example.com";
            int age = 18 + random.nextInt(50);

            User user = User.builder()
                    .password("<PASSWORD>")
                    .username(username)
                    .email(email)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            users.add(user);
        }

        return users;
    }
}
