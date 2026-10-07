package com.aresstack.corenth.proasteion.emporion.deigma.office;

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
 * The Office library is an implementation detail: it must not appear in the module's public surface.
 */
public class OfficePublicContractTest {

    private static final String[] LIBRARY_PACKAGES = {"org.apache.poi", "org.apache.xmlbeans", "org.openxmlformats"};

    @Test
    public void extractorsAreExposedOnlyThroughTheDeigmaPort() {
        for (Class<?> type : new Class<?>[] {DocxDocumentExtractor.class, XlsxDocumentExtractor.class}) {
            assertTrue(ResourceExtractor.class.isAssignableFrom(type));
            assertEquals(1, type.getInterfaces().length);
            assertTrue(Modifier.isFinal(type.getModifiers()));
        }
        assertFalse(Modifier.isPublic(OfficeExtraction.class.getModifiers()));
        assertFalse(Modifier.isPublic(OfficeDocumentLoader.class.getModifiers()));
    }

    @Test
    public void publicSignaturesDoNotReferenceLibraryTypes() {
        List<String> leaks = new ArrayList<String>();
        for (Class<?> type : new Class<?>[] {DocxDocumentExtractor.class, XlsxDocumentExtractor.class}) {
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
        }
        assertTrue("library types leak through " + leaks, leaks.isEmpty());
    }

    private static boolean isExposed(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static void check(String member, Type[] types, List<String> leaks) {
        for (Type candidate : types) {
            for (String libraryPackage : LIBRARY_PACKAGES) {
                if (candidate.getTypeName().contains(libraryPackage)) {
                    leaks.add(member);
                }
            }
        }
    }
}
