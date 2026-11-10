package com.endangered.species.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.endangered.species.domain.Category;
import com.endangered.species.domain.Region;
import com.endangered.species.domain.SpeciesRecord;
import com.endangered.species.web.Dtos;
import org.junit.jupiter.api.Test;

class ForecastServiceTest {

    private final ForecastService service = new ForecastService();

    @Test
    void extrapolatesAGrowingPopulation() {
        ForecastService.Forecast forecast = service.forecast(
                record(76, 85, 80, 92, 100, 110).getPopulations());

        assertThat(forecast.history()).containsExactly(76, 85, 80, 92, 100, 110);
        assertThat(forecast.predictions())
                .extracting(Dtos.PopulationPoint::year)
                .containsExactly(2026, 2028);
        assertThat(forecast.predictions())
                .extracting(Dtos.PopulationPoint::population)
                .containsExactly(113, 119);
        assertThat(forecast.trend()).isEqualTo("Increasing");
        assertThat(forecast.risk()).isEqualTo("Critical");
    }

    @Test
    void reportsADeclineWithTheLastCountAsTheRiskBase() {
        ForecastService.Forecast forecast = service.forecast(
                record(9_000, 8_000, 7_000, 6_000, 5_000, 4_000).getPopulations());

        assertThat(forecast.trend()).isEqualTo("Declining");
        assertThat(forecast.risk()).isEqualTo("Vulnerable");
    }

    @Test
    void keepsAFlatSeriesStable() {
        ForecastService.Forecast forecast = service.forecast(
                record(500, 500, 500, 500, 500, 500).getPopulations());

        assertThat(forecast.trend()).isEqualTo("Stable");
        assertThat(forecast.predictions())
                .extracting(Dtos.PopulationPoint::population)
                .containsExactly(500, 500);
        assertThat(forecast.risk()).isEqualTo("Endangered");
    }

    @Test
    void bucketsRiskAtTheDocumentedBoundaries() {
        assertThat(ForecastService.riskLevel(249)).isEqualTo("Critical");
        assertThat(ForecastService.riskLevel(250)).isEqualTo("Endangered");
        assertThat(ForecastService.riskLevel(2_499)).isEqualTo("Endangered");
        assertThat(ForecastService.riskLevel(2_500)).isEqualTo("Vulnerable");
        assertThat(ForecastService.riskLevel(9_999)).isEqualTo("Vulnerable");
        assertThat(ForecastService.riskLevel(10_000)).isEqualTo("Stable");
    }

    private static SpeciesRecord record(int... populations) {
        SpeciesRecord record = new SpeciesRecord(Region.INDIA, Category.TERRESTRIAL,
                "Bengal Tiger", "tiger", "Panthera tigris tigris",
                "andhra pradesh", "Sundarbans National Park", "Endangered");
        for (int i = 0; i < populations.length && i < ForecastService.YEARS.length; i++) {
            record.addPopulation(ForecastService.YEARS[i], populations[i]);
        }
        return record;
    }
}
