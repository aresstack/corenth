package com.aresstack.corenth.proasteion.emporion.deigma.html;

import com.aresstack.corenth.proasteion.emporion.deigma.ResourceExtractor;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * The parser library is an implementation detail: it must not appear in the module's public surface.
 */
public class HtmlPublicContractTest {

    private static final String PARSER_PACKAGE = "org.jsoup";

    @Test
    public void extractorIsExposedOnlyThroughTheDeigmaPort() {
        assertTrue(ResourceExtractor.class.isAssignableFrom(HtmlDocumentExtractor.class));
        assertEquals(1, HtmlDocumentExtractor.class.getInterfaces().length);
        assertTrue(Modifier.isFinal(HtmlDocumentExtractor.class.getModifiers()));
    }

    @Test
    public void helpersAreNotPublic() {
        assertFalse(Modifier.isPublic(HtmlBlockCollector.class.getModifiers()));
        assertFalse(Modifier.isPublic(HtmlCharsetHint.class.getModifiers()));
    }

    @Test
    public void publicSignaturesDoNotReferenceParserTypes() {
        List<String> leaks = new ArrayList<String>();
        Class<?> type = HtmlDocumentExtractor.class;
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (isExposed(constructor.getModifiers())) {
                check(constructor.toGenericString(), constructor.getGenericParameterTypes(), leaks);
                check(constructor.toGenericString(), constructor.getGenericExceptionTypes(), leaks);
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (isExposed(method.getModifiers())) {
                check(method.toGenericString(), new Type[] {method.getGenericReturnType()}, leaks);
                check(method.toGenericString(), method.getGenericParameterTypes(), leaks);
                check(method.toGenericString(), method.getGenericExceptionTypes(), leaks);
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (isExposed(field.getModifiers())) {
                check(field.toGenericString(), new Type[] {field.getGenericType()}, leaks);
            }
        }
        assertTrue("parser types leak through " + leaks, leaks.isEmpty());
    }

    private static boolean isExposed(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static void check(String member, Type[] types, List<String> leaks) {
        for (Type candidate : types) {
            if (candidate.getTypeName().contains(PARSER_PACKAGE)) {
                leaks.add(member);
            }
        }
    }
}
