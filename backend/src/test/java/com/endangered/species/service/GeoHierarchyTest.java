package com.endangered.species.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeoHierarchyTest {

    private final GeoHierarchy geo = new GeoHierarchy();

    @Test
    void treatsIndianStatesAsIndia() {
        assertThat(geo.countryOf("West Bengal")).isEqualTo("india");
        assertThat(geo.countryOf("  delhi ")).isEqualTo("india");
    }

    @Test
    void treatsPanAsianSheetsAsCountries() {
        assertThat(geo.countryOf("Nepal")).isEqualTo("nepal");
        assertThat(geo.continentOf("Nepal")).isEqualTo("asia");
        assertThat(geo.continentOf("South Africa")).isEqualTo("africa");
    }

    @Test
    void resolvesGlobalSheetContinentsIncludingTheWorkbookAlias() {
        assertThat(geo.continentOf("asia")).isEqualTo("asia");
        assertThat(geo.continentOf("australia / oceania")).isEqualTo("oceania");
        assertThat(geo.continentOf("north america")).isEqualTo("north america");
    }

    @Test
    void returnsNullForUnknownOrBlankValues() {
        assertThat(GeoHierarchy.normalize("   ")).isNull();
        assertThat(GeoHierarchy.normalize(null)).isNull();
        assertThat(geo.continentOf("atlantis")).isNull();
    }
}
