package com.aresstack.corenth.proasteion.application;

import com.aresstack.corenth.astu.VirtualResourceRef;
import com.aresstack.corenth.astu.acropolis.chalcotheca.BronzeContent;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResourceAccess;
import com.aresstack.corenth.astu.acropolis.chalcotheca.MediatedResult;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ActorIdentity;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceAccessRequest;
import com.aresstack.corenth.astu.acropolis.chalcotheca.tamias.ResourceOperation;
import com.aresstack.corenth.proasteion.katagogion.MediatedReading;
import com.aresstack.corenth.proasteion.katagogion.ReadOutcome;
import com.aresstack.corenth.proasteion.katagogion.ToolCapabilities;
import com.aresstack.corenth.proasteion.katagogion.mediation.SearchCoordinatorLexicalSearch;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

/**
 * Binds the composed use cases to Katagogion tool capabilities.
 *
 * <p>Search goes through the composed {@code SearchCoordinator}. Reading goes through the
 * composed {@link MediatedResourceAccess} under an actor the host chooses here, so Tamias decides
 * every tool read and a tool can neither pick nor change its actor. Only the Tamias outcome
 * kind and its explanation reach the tool, never the decision object or connector details.
 */
public final class ToolCapabilityBinding {

    private static final String UTF_8 = "UTF-8";
    private static final String TOOL_READ_PURPOSE = "katagogion tool read";

    private ToolCapabilityBinding() {
    }

    /**
     * Create the capabilities offered to tools of the given application.
     *
     * @param application the composed application
     * @param toolActor   the actor under which tool reads are requested
     * @return search and mediated reading capabilities
     */
    public static ToolCapabilities bind(CorenthApplication application, ActorIdentity toolActor) {
        if (application == null) throw new IllegalArgumentException("application must not be null");
        if (toolActor == null) throw new IllegalArgumentException("toolActor must not be null");
        return ToolCapabilities.builder()
                .lexicalSearch(new SearchCoordinatorLexicalSearch(application.search()))
                .mediatedReading(new ActorBoundReading(application.mediatedResourceAccess(), toolActor))
                .build();
    }

    /** Mediated reading under a fixed actor, decoding UTF-8 text only. */
    private static final class ActorBoundReading implements MediatedReading {
        private final MediatedResourceAccess access;
        private final ActorIdentity actor;

        ActorBoundReading(MediatedResourceAccess access, ActorIdentity actor) {
            this.access = access;
            this.actor = actor;
        }

        @Override
        public ReadOutcome read(VirtualResourceRef resourceRef) {
            if (resourceRef == null) {
                return ReadOutcome.unavailable("no resource");
            }
            MediatedResult<BronzeContent> result = access.readContent(new ResourceAccessRequest(
                    actor, resourceRef.uri(), ResourceOperation.READ_CONTENT, TOOL_READ_PURPOSE));
            if (result.isSuccess()) {
                return decode(result.value());
            }
            if (result.decision() != null) {
                return ReadOutcome.denied(result.decision().reasonCode() + ": " + result.decision().explanation());
            }
            return ReadOutcome.unavailable(String.valueOf(result.failure()));
        }

        private static ReadOutcome decode(BronzeContent content) {
            try {
                String text = Charset.forName(UTF_8).newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(content.content()))
                        .toString();
                return ReadOutcome.content(text, "text/plain; charset=UTF-8");
            } catch (CharacterCodingException e) {
                return ReadOutcome.unavailable("content is not UTF-8 text");
            }
        }
    }
}
