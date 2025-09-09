package annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks tests that require windowing system and display capabilities.
 *
 * <p>Tests annotated with this will be automatically excluded in headless CI environments where no
 * display is available. This prevents GUI-related tests from failing in environments that cannot
 * support windowing systems.
 *
 * <p>excluded when audio hardware is not available.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Windowing {}
