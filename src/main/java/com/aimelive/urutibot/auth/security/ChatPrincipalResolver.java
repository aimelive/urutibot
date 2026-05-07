package com.aimelive.urutibot.auth.security;

import com.aimelive.urutibot.shared.config.CacheConfig;
import com.aimelive.urutibot.auth.model.User;
import com.aimelive.urutibot.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatPrincipalResolver {

    private final ChatAuthContext authContext;
    private final UserRepository userRepository;

    @Autowired
    @Lazy
    private ChatPrincipalResolver self;

    public Optional<ChatAuthContext.AuthSnapshot> resolveSnapshot(String memoryId) {
        return authContext.get(memoryId);
    }

    public Optional<User> resolveUser(String memoryId) {
        return authContext.get(memoryId)
                .map(ChatAuthContext.AuthSnapshot::userId)
                .flatMap(self::findUserById);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.USERS_BY_ID, key = "#userId", unless = "#result == null")
    public Optional<User> findUserById(UUID userId) {
        return userRepository.findWithAuthoritiesById(userId);
    }
}
