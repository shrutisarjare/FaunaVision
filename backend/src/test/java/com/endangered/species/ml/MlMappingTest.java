package com.endangered.species.ml;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MlMappingTest {

    @Test
    void mapsAudioSetLabelsOntoCatalogueTypes() {
        assertThat(AudioClassifier.typesFor("Roaring cats (lions, tigers)"))
                .containsExactly("tiger", "lion", "leopard");
        assertThat(AudioClassifier.typesFor("Bird vocalization, bird call, bird song"))
                .containsExactly("bird");
        assertThat(AudioClassifier.typesFor("Owl")).containsExactly("owl");
        assertThat(AudioClassifier.typesFor("Pigeon, dove")).containsExactly("pigeon");
        assertThat(AudioClassifier.typesFor("Canidae, dogs, wolves")).containsExactly("wolf");
        assertThat(AudioClassifier.typesFor("Speech")).isEmpty();
        assertThat(AudioClassifier.typesFor(null)).isEmpty();
    }

    @Test
    void readsTheBundledAudioSetLabelNames() {
        AudioSetLabels labels = AudioSetLabels.load();

        assertThat(labels.size()).isEqualTo(521);
        assertThat(labels.name(104)).isEqualTo("Roaring cats (lions, tigers)");
        assertThat(labels.name(106)).isEqualTo("Bird");
    }

    @Test
    void interpretsScoresThroughLabelNamesInsteadOfIndexRanges() {
        AudioClassifier classifier = new AudioClassifier(null, AudioSetLabels.load());

        float[] birdsong = new float[521];
        birdsong[106] = 0.8f;
        birdsong[107] = 0.6f;
        AudioClassifier.Evidence evidence = classifier.interpret(birdsong);

        assertThat(evidence.animalTypes()).containsExactly("bird");
        assertThat(evidence.confidence()).isEqualTo(0.8f);
        assertThat(evidence.label()).isEqualTo("Bird");

        float[] speech = new float[521];
        speech[0] = 0.9f;
        assertThat(classifier.interpret(speech).isEmpty()).isTrue();
    }

    @Test
    void foldsTheGenericBirdClassIntoTheSpecificOne() {
        AudioClassifier classifier = new AudioClassifier(null, AudioSetLabels.load());

        float[] hoot = new float[521];
        hoot[114] = 0.7f;
        hoot[106] = 0.3f;
        AudioClassifier.Evidence evidence = classifier.interpret(hoot);

        // An owl hoot is also a bird sound; listing both would split the evidence.
        assertThat(evidence.animalTypes()).containsExactly("owl");
        assertThat(evidence.typeScores().get("owl")).isEqualTo(1.0f);
    }

    @Test
    void collapsesVerboseImageNetLabelsToSpeciesNames() {
        assertThat(LabelCleaner.clean("Bengal tiger, Panthera tigris tigris")).isEqualTo("tiger");
        assertThat(LabelCleaner.clean("African elephant")).isEqualTo("elephant");
        assertThat(LabelCleaner.clean("Timber wolf, grey wolf")).isEqualTo("wolf");
        assertThat(LabelCleaner.clean("  SLOTH BEAR ")).isEqualTo("bear");
        assertThat(LabelCleaner.clean("Sambar deer")).isEqualTo("sambar deer");
        assertThat(LabelCleaner.clean(null)).isEmpty();
    }

    @Test
    void parsesTheSynsetListAndFallsBackToClassIndexes(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("imagenet-synset.txt");
        Files.write(file, List.of(
                "n01440764 tench, Tinca tinca",
                "n02123159 tiger cat",
                "",
                "# a comment"));

        ImageNetLabels labels = ImageNetLabels.loadOrFallback(file);

        assertThat(labels.size()).isEqualTo(2);
        assertThat(labels.label(0)).isEqualTo("tench");
        assertThat(labels.label(1)).isEqualTo("tiger cat");
        assertThat(labels.label(7)).isEqualTo("class 7");
    }

    @Test
    void toleratesAMissingLabelFile(@TempDir Path tempDir) {
        ImageNetLabels labels = ImageNetLabels.loadOrFallback(tempDir.resolve("absent.txt"));

        assertThat(labels.size()).isZero();
        assertThat(labels.label(3)).isEqualTo("class 3");
    }
}
