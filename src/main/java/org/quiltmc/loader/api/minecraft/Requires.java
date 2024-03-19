package org.quiltmc.loader.api.minecraft;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Applied to declare that the annotated element specific mods to exist.
 * <p>
 * When applied to mod code this will result in quilt-loader removing that element when running without the specified mods.
 * <p>
 * When the annotated element is removed, bytecode associated with the element will not be removed. For example, if a
 * field is removed, its initializer code will not, and will cause an error on execution.
 * <p>
 * If an overriding method has this annotation and its overridden method doesn't, unexpected behavior may happen. If an
 * overridden method has this annotation while the overriding method doesn't, it is safe, but the method can be used
 * from the overridden class only when specified mods are found.
 * <p>
 * When applied to an implemented interface (via {@link ElementType#TYPE_USE}) it will be removed when running without the specified mods.
 * Overridden interface methods need to be annotated separately in order to be removed. */
@Retention(RetentionPolicy.CLASS)
@Target({ ElementType.TYPE, ElementType.TYPE_USE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PACKAGE })
@Documented
public @interface Requires {

	/** @return required mods */
	String[] mods();

	/** @return True if lambda methods referenced by this method should also be stripped. Has no effect when used to
	 *         annotate classes, implements declarations, or fields. */
	boolean stripLambdas() default true;
}
