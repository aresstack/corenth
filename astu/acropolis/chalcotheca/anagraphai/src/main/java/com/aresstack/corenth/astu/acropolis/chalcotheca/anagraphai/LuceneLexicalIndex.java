package com.aresstack.corenth.astu.acropolis.chalcotheca.anagraphai;

import com.aresstack.corenth.astu.BookmarkUri;
import com.aresstack.corenth.astu.VirtualResourceKind;
import com.aresstack.corenth.astu.VirtualResourceRef;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Lucene-backed implementation of {@link LexicalIndex}.
 *
 * <p>Uses Lucene 8.11.x with the BM25 similarity (default in Lucene 8+) for
 * full-text retrieval. Documents are stored per-chunk with a composite resource
 * key (URI + kind) for update/delete operations.
 *
 * <p>This implementation uses an explicit commit model: changes made via
 * {@link #index(LexicalDocument)} and {@link #remove(VirtualResourceRef)} are
 * only visible to {@link #search(LexicalQuery)} after {@link #commit()} is called.
 *
 * <p>Adapted from MainframeMate's {@code LuceneLexicalIndex} and
 * {@code LuceneDependencyIndex}, with Corenth resource identity and
 * injectable configuration replacing the original's global path assumptions.
 */
public final class LuceneLexicalIndex implements LexicalIndex {

    static final String FIELD_RESOURCE_KEY = "resourceKey";
    static final String FIELD_RESOURCE_URI = "resourceUri";
    static final String FIELD_RESOURCE_KIND = "resourceKind";
    static final String FIELD_TITLE = "title";
    static final String FIELD_CONTENT_TYPE = "contentType";
    static final String FIELD_CHUNK_INDEX = "chunkIndex";
    static final String FIELD_CHUNK_INDEX_STORED = "chunkIndexStored";
    static final String FIELD_CONTENT = "content";

    private final LexicalIndexConfig config;
    private final Directory directory;
    private final IndexWriter writer;
    private final StandardAnalyzer analyzer;

    /**
     * Opens or creates a Lucene index at the configured directory.
     *
     * @param config the index configuration
     * @throws IOException if the index directory cannot be opened or created
     */
    public LuceneLexicalIndex(LexicalIndexConfig config) throws IOException {
        this.config = Objects.requireNonNull(config, "config must not be null");
        Files.createDirectories(config.indexDirectory());
        this.directory = FSDirectory.open(config.indexDirectory());
        this.analyzer = new StandardAnalyzer();
        IndexWriterConfig writerConfig = new IndexWriterConfig(analyzer);
        writerConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        writerConfig.setCommitOnClose(false);
        this.writer = new IndexWriter(directory, writerConfig);
    }

    @Override
    public void index(LexicalDocument document) throws IOException {
        String resourceKey = toResourceKey(document.resourceRef());
        String resourceUri = document.resourceRef().uri().toString();
        String resourceKind = document.resourceRef().kind().name();

        // Remove existing documents for this resource first (update semantics)
        writer.deleteDocuments(new Term(FIELD_RESOURCE_KEY, resourceKey));

        for (LexicalChunk chunk : document.chunks()) {
            Document doc = new Document();
            doc.add(new StringField(FIELD_RESOURCE_KEY, resourceKey, Field.Store.NO));
            doc.add(new StringField(FIELD_RESOURCE_URI, resourceUri, Field.Store.YES));
            doc.add(new StringField(FIELD_RESOURCE_KIND, resourceKind, Field.Store.YES));
            if (document.title() != null) {
                doc.add(new TextField(FIELD_TITLE, document.title(), Field.Store.YES));
            }
            if (document.contentType() != null) {
                doc.add(new StringField(FIELD_CONTENT_TYPE, document.contentType(), Field.Store.YES));
            }
            doc.add(new IntPoint(FIELD_CHUNK_INDEX, chunk.index()));
            doc.add(new StoredField(FIELD_CHUNK_INDEX_STORED, chunk.index()));
            doc.add(new TextField(FIELD_CONTENT, chunk.text(), Field.Store.YES));
            writer.addDocument(doc);
        }
    }

    @Override
    public List<LexicalSearchResult> search(LexicalQuery query) throws IOException {
        DirectoryReader reader = null;
        try {
            if (!DirectoryReader.indexExists(directory)) {
                return Collections.emptyList();
            }
            reader = DirectoryReader.open(directory);
            if (reader.numDocs() == 0) {
                return Collections.emptyList();
            }

            IndexSearcher searcher = new IndexSearcher(reader);
            String escapedQueryText = QueryParser.escape(query.queryText());

            // Build query: search both content and title fields
            BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();
            try {
                QueryParser contentParser = new QueryParser(FIELD_CONTENT, analyzer);
                contentParser.setDefaultOperator(QueryParser.Operator.OR);
                booleanQuery.add(contentParser.parse(escapedQueryText),
                        BooleanClause.Occur.SHOULD);

                QueryParser titleParser = new QueryParser(FIELD_TITLE, analyzer);
                titleParser.setDefaultOperator(QueryParser.Operator.OR);
                booleanQuery.add(titleParser.parse(escapedQueryText),
                        BooleanClause.Occur.SHOULD);
            } catch (ParseException e) {
                throw new IllegalArgumentException("Invalid lexical query: " + query.queryText(), e);
            }

            TopDocs topDocs = searcher.search(booleanQuery.build(), query.maxResults());
            List<LexicalSearchResult> results = new ArrayList<LexicalSearchResult>();

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                String uri = doc.get(FIELD_RESOURCE_URI);
                String kind = doc.get(FIELD_RESOURCE_KIND);
                String title = doc.get(FIELD_TITLE);
                String contentType = doc.get(FIELD_CONTENT_TYPE);
                String content = doc.get(FIELD_CONTENT);
                int chunkIndex = doc.getField(FIELD_CHUNK_INDEX_STORED) != null
                        ? doc.getField(FIELD_CHUNK_INDEX_STORED).numericValue().intValue()
                        : 0;

                BookmarkUri bookmarkUri = BookmarkUri.parse(uri);
                VirtualResourceKind resourceKind = VirtualResourceKind.valueOf(kind);
                VirtualResourceRef ref = new VirtualResourceRef(bookmarkUri, resourceKind);

                results.add(new LexicalSearchResult(ref, scoreDoc.score,
                        chunkIndex, content, title, contentType));
            }
            return results;
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    @Override
    public void remove(VirtualResourceRef resourceRef) throws IOException {
        String resourceKey = toResourceKey(resourceRef);
        writer.deleteDocuments(new Term(FIELD_RESOURCE_KEY, resourceKey));
    }

    @Override
    public void commit() throws IOException {
        writer.commit();
    }

    @Override
    public void close() throws IOException {
        try {
            writer.close();
        } finally {
            try {
                analyzer.close();
            } finally {
                directory.close();
            }
        }
    }

    /**
     * Builds the canonical resource key from a {@link VirtualResourceRef}.
     * The key includes both URI and kind to prevent collisions between
     * resources that share the same URI but have different kinds.
     */
    private static String toResourceKey(VirtualResourceRef ref) {
        return ref.uri().toString() + "\n" + ref.kind().name();
    }

}
