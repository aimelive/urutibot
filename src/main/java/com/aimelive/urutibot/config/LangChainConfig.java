package com.aimelive.urutibot.config;

import com.aimelive.urutibot.service.DurableChatMemory;
import com.aimelive.urutibot.service.DurableChatMemoryGateway;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.HuggingFaceTokenizer;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class LangChainConfig {

    private static final int PROMPT_WINDOW_MESSAGES = 10;

    @Value("${app.about-company-file}")
    private Resource urutiHubFile;

    @Bean
    Tokenizer tokenizer() {
        return new HuggingFaceTokenizer();
    }


    @Bean
    EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    EmbeddingStore<TextSegment> embeddingStore(EmbeddingModel embeddingModel,
                                               Tokenizer tokenizer)
            throws IOException {

        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
        Document document;
        try (InputStream in = urutiHubFile.getInputStream()) {
            document = new TextDocumentParser().parse(in);
        }

        DocumentSplitter documentSplitter = new DocumentBySentenceSplitter(100,
                10, tokenizer);

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
        ingestor.ingest(document);

        return embeddingStore;
    }

    @Bean
    ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {

        int maxResults = 5;
        double minScore = 0.6;

        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(maxResults)
                .minScore(minScore)
                .build();
    }

    @Bean
    ChatMemoryProvider chatMemoryProvider(DurableChatMemoryGateway gateway) {
        return memoryId -> new DurableChatMemory(
                memoryId.toString(), PROMPT_WINDOW_MESSAGES, gateway);
    }
}
