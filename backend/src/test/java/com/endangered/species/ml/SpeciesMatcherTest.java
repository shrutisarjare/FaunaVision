package com.endangered.species.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

class SpeciesMatcherTest {

    /** A unit vector in the plane, which is all the ranking needs. */
    private static float[] vector(double x, double y) {
        double length = Math.hypot(x, y);
        return new float[]{(float) (x / length), (float) (y / length)};
    }

    private static SpeciesMatcher matcherWith(SpeciesIndex.Entry... entries) {
        SpeciesEmbedder embedder = mock(SpeciesEmbedder.class);
        when(embedder.index()).thenReturn(new SpeciesIndex("test", List.of(entries)));
        return new SpeciesMatcher(embedder);
    }

    @Test
    void keepsOneRowPerNameAndItsBestVariant() {
        // The catalogue lists Panthera pardus twice; only the better prompt should survive.
        SpeciesMatcher matcher = matcherWith(
                new SpeciesIndex.Entry("Leopard", "Panthera pardus", "leopard", vector(1, 0.20)),
                new SpeciesIndex.Entry("Leopard", "Panthera pardus fusca", "leopard", vector(1, 0.05)),
                new SpeciesIndex.Entry("Cheetah", "Acinonyx jubatus", "cheetah", vector(0.6, 0.8)));

        List<SpeciesMatcher.Scored> ranked = matcher.rank(matcher.scores(vector(1, 0)));

        assertThat(ranked).extracting(scored -> scored.entry().name())
                .containsExactly("Leopard", "Cheetah");
        // The closer variant decides the score, not the average of both.
        assertThat(ranked.get(0).similarity()).isGreaterThan(0.995f);
    }

    @Test
    void aSpelledOutNameOutranksANearTie() {
        SpeciesMatcher matcher = matcherWith(
                new SpeciesIndex.Entry("Giant Panda", "Ailuropoda melanoleuca", "bear", vector(0.995, 0.0999)),
                new SpeciesIndex.Entry("Brown Bear", "Ursus arctos", "bear", vector(1, 0)));
        float[] scores = matcher.scores(vector(1, 0));

        assertThat(matcher.rank(scores).get(0).entry().name()).isEqualTo("Brown Bear");

        matcher.boostMentions(scores, "the giant panda eats bamboo");

        assertThat(matcher.rank(scores).get(0).entry().name()).isEqualTo("Giant Panda");
    }

    @Test
    void averagesFramesInsteadOfLettingOneOfThemWin() {
        float[] merged = SpeciesMatcher.mergeByMean(new float[]{1f, 0f}, new float[]{0f, 1f}, 2);

        assertThat(merged).containsExactly(0.5f, 0.5f);
    }

    @Test
    void aSoundtrackBonusReordersNearTiesOnly() {
        SpeciesMatcher matcher = matcherWith(
                new SpeciesIndex.Entry("Bengal Tiger", "Panthera tigris", "tiger", vector(1, 0)),
                new SpeciesIndex.Entry("Asiatic Lion", "Panthera leo persica", "lion", vector(1, 0.15)),
                new SpeciesIndex.Entry("Blue Whale", "Balaenoptera musculus", "whale", vector(0.2, 1)));
        float[] scores = matcher.scores(vector(1, 0));

        assertThat(matcher.rank(scores).get(0).entry().name()).isEqualTo("Bengal Tiger");

        matcher.boostTypes(scores, List.of("lion"), 0.015f);

        List<SpeciesMatcher.Scored> ranked = matcher.rank(scores);
        assertThat(ranked.get(0).entry().name()).isEqualTo("Asiatic Lion");
        assertThat(ranked.get(ranked.size() - 1).entry().name()).isEqualTo("Blue Whale");
    }
}
