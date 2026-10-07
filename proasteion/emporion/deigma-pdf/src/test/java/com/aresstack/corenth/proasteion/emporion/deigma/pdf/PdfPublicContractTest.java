package com.aresstack.corenth.proasteion.emporion.deigma.pdf;

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
 * The PDF library is an implementation detail: it must not appear in the module's public surface.
 */
public class PdfPublicContractTest {

    private static final String LIBRARY_PACKAGE = "org.apache.pdfbox";

    @Test
    public void extractorIsExposedOnlyThroughTheDeigmaPort() {
        assertTrue(ResourceExtractor.class.isAssignableFrom(PdfDocumentExtractor.class));
        assertEquals(1, PdfDocumentExtractor.class.getInterfaces().length);
        assertTrue(Modifier.isFinal(PdfDocumentExtractor.class.getModifiers()));
        assertFalse(Modifier.isPublic(PdfDocumentLoader.class.getModifiers()));
    }

    @Test
    public void publicSignaturesDoNotReferenceLibraryTypes() {
        List<String> leaks = new ArrayList<String>();
        Class<?> type = PdfDocumentExtractor.class;
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
        assertTrue("library types leak through " + leaks, leaks.isEmpty());
    }

    private static boolean isExposed(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static void check(String member, Type[] types, List<String> leaks) {
        for (Type candidate : types) {
            if (candidate.getTypeName().contains(LIBRARY_PACKAGE)) {
                leaks.add(member);
            }
        }
    }
}
