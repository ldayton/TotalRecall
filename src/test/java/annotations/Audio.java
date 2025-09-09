package annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks tests that use the FMOD audio engine (library load/format detection/decoding), regardless
 * of whether physical audio hardware is required. These tests should run serialized to avoid
 * contention with other FMOD users in the same JVM.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Audio {}
