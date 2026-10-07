package com.aresstack.corenth.astu.propylaea;

import com.aresstack.corenth.astu.propylaea.cobol.CobolSourceParser;
import com.aresstack.corenth.astu.propylaea.cobol.CobolSourceParserTest;
import com.aresstack.corenth.astu.propylaea.jcl.JclSourceParser;
import com.aresstack.corenth.astu.propylaea.jcl.JclSourceParserTest;
import com.aresstack.corenth.astu.propylaea.natural.NaturalSourceParser;
import com.aresstack.corenth.astu.propylaea.natural.NaturalSourceParserTest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.aresstack.corenth.astu.propylaea.StructureText.fixture;
import static com.aresstack.corenth.astu.propylaea.StructureText.request;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceParserRegistryTest {

    private static final SourceLanguageDetector DETECTOR = new KeywordSourceLanguageDetector();

    /** Records the language it was asked to parse. */
    private static final class RecordingParser implements SourceParser {

        private final SourceLanguage language;
        private SourceLanguage requested;

        RecordingParser(SourceLanguage language) {
            this.language = language;
        }

        @Override
        public SourceLanguage language() {
            return language;
        }

        @Override
        public ParsingResult parse(ParsingRequest request) {
            requested = request.language();
            return ParsingResult.failure(request.source(), language, ParsingFailureReason.EMPTY_SOURCE, "recorded");
        }
    }

    private static SourceParserRegistry registry(SourceParser... parsers) {
        return new SourceParserRegistry(DETECTOR, Arrays.asList(parsers));
    }

    @Test
    void dispatchesByLanguageHint() {
        RecordingParser jcl = new RecordingParser(SourceLanguage.JCL);
        RecordingParser cobol = new RecordingParser(SourceLanguage.COBOL);

        registry(jcl, cobol).parse(request("X", SourceLanguage.COBOL, "text"));

        assertEquals(SourceLanguage.COBOL, cobol.requested);
        assertEquals(null, jcl.requested);
    }

    @Test
    void unknownHint_isDetected_andPassedOnAsConcreteLanguage() {
        RecordingParser natural = new RecordingParser(SourceLanguage.NATURAL);

        registry(natural).parse(request("X", SourceLanguage.UNKNOWN, "DEFINE DATA LOCAL\nEND-DEFINE\nEND\n"));

        assertEquals(SourceLanguage.NATURAL, natural.requested);
    }

    @Test
    void missingParser_isUnsupportedLanguage() {
        ParsingResult result = registry(new NaturalSourceParser()).parse(request("X", SourceLanguage.JCL, "//J JOB"));

        assertFalse(result.isSuccess());
        assertEquals(ParsingFailureReason.UNSUPPORTED_LANGUAGE, result.failureReason());
        assertEquals(SourceLanguage.JCL, result.language());
    }

    @Test
    void undetectableLanguage_isUnsupportedLanguage() {
        ParsingResult result = registry(new NaturalSourceParser()).parse(request("X", SourceLanguage.UNKNOWN, "hello world"));

        assertEquals(ParsingFailureReason.UNSUPPORTED_LANGUAGE, result.failureReason());
        assertEquals(SourceLanguage.UNKNOWN, result.language());
    }

    @Test
    void realParser_isReachableThroughRegistry() {
        ParsingResult result = registry(new NaturalSourceParser())
                .parse(request("P", SourceLanguage.UNKNOWN, "DEFINE DATA LOCAL\nEND-DEFINE\nCALLNAT 'SUB'\nEND\n"));

        assertTrue(result.isSuccess());
        assertEquals(1, result.structure().relations(RelationKind.CALLS).size());
    }

    @Test
    void duplicateOrUnknownLanguageParsers_areRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> registry(new RecordingParser(SourceLanguage.JCL), new RecordingParser(SourceLanguage.JCL)));
        assertThrows(IllegalArgumentException.class, () -> registry(new RecordingParser(SourceLanguage.UNKNOWN)));
        assertThrows(IllegalArgumentException.class, () -> new SourceParserRegistry(null, Collections.<SourceParser>emptyList()));
    }

    @Test
    void supportedLanguages_areListedInEnumOrder_andRegistryIsImmutable() {
        List<SourceParser> parsers = new java.util.ArrayList<SourceParser>();
        parsers.add(new RecordingParser(SourceLanguage.JCL));
        parsers.add(new RecordingParser(SourceLanguage.NATURAL));
        SourceParserRegistry registry = new SourceParserRegistry(DETECTOR, parsers);
        parsers.add(new RecordingParser(SourceLanguage.COBOL));

        assertEquals(Arrays.asList(SourceLanguage.NATURAL, SourceLanguage.JCL), registry.supportedLanguages());
        assertFalse(registry.supports(SourceLanguage.COBOL));
    }

    @Test
    void allReferenceParsers_detectAndParseTheirFixturesWithoutHints() {
        SourceParserRegistry registry = new SourceParserRegistry(DETECTOR, Arrays.<SourceParser>asList(
                new NaturalSourceParser(), new JclSourceParser(), new CobolSourceParser()));

        ParsingResult natural = registry.parse(request("CUSTUPD", SourceLanguage.UNKNOWN,
                fixture(NaturalSourceParserTest.class, "CUSTUPD.NSP")));
        ParsingResult jcl = registry.parse(request("PAYJOB", SourceLanguage.UNKNOWN,
                fixture(JclSourceParserTest.class, "PAYJOB.JCL")));
        ParsingResult cobol = registry.parse(request("ACCTUPD", SourceLanguage.UNKNOWN,
                fixture(CobolSourceParserTest.class, "ACCTUPD.cbl")));

        assertEquals(SourceLanguage.NATURAL, natural.structure().language());
        assertEquals(SourceLanguage.JCL, jcl.structure().language());
        assertEquals(SourceLanguage.COBOL, cobol.structure().language());
        assertEquals(Arrays.asList(SourceLanguage.NATURAL, SourceLanguage.COBOL, SourceLanguage.JCL),
                registry.supportedLanguages());
    }
}
