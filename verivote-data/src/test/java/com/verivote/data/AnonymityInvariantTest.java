package com.verivote.data;

import com.verivote.data.entity.SpentToken;
import com.verivote.data.entity.Vote;
import com.verivote.data.entity.Voter;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

class AnonymityInvariantTest {

    private static final Set<String> FORBIDDEN_NAMES = Set.of(
            "voter", "voterid", "voter_id"
    );

    private static final List<Class<?>> ANONYMOUS_ENTITIES = List.of(Vote.class, SpentToken.class);

    @Test
    @DisplayName("1. Reflection walk: no field of type Voter or named voter/voterId/voter_id")
    void noVoterFields() {
        for (Class<?> clazz : ANONYMOUS_ENTITIES) {
            for (Field field : clazz.getDeclaredFields()) {
                assertNotEquals(Voter.class, field.getType(),
                        clazz.getSimpleName() + " must not have any field of type Voter");

                String lowerName = field.getName().toLowerCase(Locale.ROOT);
                assertFalse(FORBIDDEN_NAMES.contains(lowerName),
                        clazz.getSimpleName() + " must not have field named " + field.getName());
            }
        }
    }

    @Test
    @DisplayName("2. Reflection walk on JPA associations: no @ManyToOne or @OneToOne targeting Voter")
    void noVoterAssociations() {
        for (Class<?> clazz : ANONYMOUS_ENTITIES) {
            for (Field field : clazz.getDeclaredFields()) {
                ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
                if (manyToOne != null) {
                    assertNotEquals(Voter.class, manyToOne.targetEntity(),
                            clazz.getSimpleName() + "." + field.getName() + " must not target Voter");
                    assertNotEquals(Voter.class, field.getType(),
                            clazz.getSimpleName() + "." + field.getName() + " must not be Voter");
                }

                OneToOne oneToOne = field.getAnnotation(OneToOne.class);
                if (oneToOne != null) {
                    assertNotEquals(Voter.class, oneToOne.targetEntity(),
                            clazz.getSimpleName() + "." + field.getName() + " must not target Voter");
                    assertNotEquals(Voter.class, field.getType(),
                            clazz.getSimpleName() + "." + field.getName() + " must not be Voter");
                }
            }
        }
    }

    @Test
    @DisplayName("3. Classpath scan: Vote has no getter/setter for Voter")
    void noVoterMethods() {
        Set<String> forbiddenMethods = Set.of("getvoter", "getvoterid", "setvoter", "setvoterid");
        for (Method method : Vote.class.getDeclaredMethods()) {
            String lowerName = method.getName().toLowerCase(Locale.ROOT);
            assertFalse(forbiddenMethods.contains(lowerName),
                    "Vote must not have method: " + method.getName());
        }
    }

    @Test
    @DisplayName("4. No timestamps allowed in SpentToken or Vote")
    void noTimestampFields() {
        Set<Class<?>> timestampTypes = Set.of(
                Instant.class,
                LocalDateTime.class,
                Timestamp.class,
                java.util.Date.class,
                java.sql.Date.class
        );

        for (Class<?> clazz : ANONYMOUS_ENTITIES) {
            for (Field field : clazz.getDeclaredFields()) {
                if (timestampTypes.contains(field.getType())) {
                    fail(clazz.getSimpleName() + " must not contain timestamp field: " + field.getName()
                            + " (" + field.getType().getSimpleName() + ")");
                }
            }
        }
    }
}
