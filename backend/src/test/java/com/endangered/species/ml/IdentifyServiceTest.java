package com.endangered.species.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.endangered.species.service.SpeciesCatalogService;
import com.endangered.species.service.WikiImageService;
import com.endangered.species.web.Dtos;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdentifyServiceTest {

    private static float[] vector(double x, double y) {
        double length = Math.hypot(x, y);
        return new float[]{(float) (x / length), (float) (y / length)};
    }

    private static Dtos.SpeciesSummary summary(String name) {
        return new Dtos.SpeciesSummary(name, "Panthera tigris", "India", "Endangered", "");
    }

    private static SpeciesMatcher matcherWith(SpeciesIndex.Entry... entries) {
        SpeciesEmbedder embedder = mock(SpeciesEmbedder.class);
        when(embedder.index()).thenReturn(new SpeciesIndex("test", List.of(entries)));
        return new SpeciesMatcher(embedder);
    }

    private static IdentifyService service(SpeciesMatcher matcher, ClipModel clip,
                                           SpeciesCatalogService catalog) {
        return new IdentifyService(mock(ImageClassifier.class), mock(AudioClassifier.class),
                clip, matcher, catalog, mock(WikiImageService.class));
    }

    @Test
    void ranksADescriptionInClipTextSpace() {
        SpeciesMatcher matcher = matcherWith(
                new SpeciesIndex.Entry("Bengal Tiger", "Panthera tigris", "tiger", vector(1, 0)),
                new SpeciesIndex.Entry("Asiatic Lion", "Panthera leo persica", "lion", vector(0.9, 0.4)));
        ClipModel clip = mock(ClipModel.class);
        when(clip.embedText(anyString())).thenReturn(vector(1, 0));
        SpeciesCatalogService catalog = mock(SpeciesCatalogService.class);
        when(catalog.search("Bengal Tiger")).thenReturn(List.of(summary("Bengal Tiger")));

        Dtos.IdentifyResult result =
                service(matcher, clip, catalog).fromText("a large orange cat with black stripes");

        assertThat(result.species()).isEqualTo("Bengal Tiger");
        assertThat(result.matches()).extracting(Dtos.Match::name)
                .containsExactly("Bengal Tiger", "Asiatic Lion");
        assertThat(result.evidence()).containsIgnoringCase("catalogue");
        verify(clip).embedText("a large orange cat with black stripes");
        // The description is a question, not a species name: the catalogue still answers it.
        verify(catalog).search("Bengal Tiger");
    }

    @Test
    void traitWordsSteerTheRankingTowardsTheRightGroup() {
        SpeciesMatcher matcher = matcherWith(
                new SpeciesIndex.Entry("Asian Elephant", "Elephas maximus", "elephant", vector(0.985, 0.1724)),
                new SpeciesIndex.Entry("Giant Panda", "Ailuropoda melanoleuca", "bear", vector(0.99, 0.14)));
        ClipModel clip = mock(ClipModel.class);
        when(clip.embedText(anyString())).thenReturn(vector(1, 0));
        SpeciesCatalogService catalog = mock(SpeciesCatalogService.class);

        Dtos.IdentifyResult result = service(matcher, clip, catalog)
                .fromText("a huge grey animal with a long trunk");

        assertThat(result.species()).isEqualTo("Asian Elephant");
    }

    @Test
    void asksForADescriptionWhenTheInputIsBlank() {
        ClipModel clip = mock(ClipModel.class);
        SpeciesCatalogService catalog = mock(SpeciesCatalogService.class);

        Dtos.IdentifyResult result = service(mock(SpeciesMatcher.class), clip, catalog).fromText("   ");

        assertThat(result.species()).isEqualTo("unknown");
        assertThat(result.results()).isEmpty();
        assertThat(result.evidence()).containsIgnoringCase("describe");
    }

    @Test
    void keepsTheKeywordChainWhenClipIsNotInstalled() {
        SpeciesMatcher matcher = matcherWith();
        ClipModel clip = mock(ClipModel.class);
        SpeciesCatalogService catalog = mock(SpeciesCatalogService.class);
        when(catalog.search("lion")).thenReturn(List.of(summary("Asiatic Lion")));

        Dtos.IdentifyResult result = service(matcher, clip, catalog).fromText("The animal ROARS loudly");

        assertThat(result.species()).isEqualTo("lion");
        assertThat(result.results()).hasSize(1);
        verify(catalog).search("lion");
    }
}
