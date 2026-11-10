package com.endangered.species.service;

import com.endangered.species.domain.PopulationEntry;
import com.endangered.species.web.Dtos;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Ordinary least squares over the six biennial observations (2014-2024),
 * extrapolated to 2026 and 2028. Same model as the original scikit-learn fit.
 */
@Service
public class ForecastService {

    public static final int[] YEARS = {2014, 2016, 2018, 2020, 2022, 2024};
    private static final int[] FUTURE_YEARS = {2026, 2028};

    public record Forecast(List<Integer> history, List<Dtos.PopulationPoint> predictions, String trend, String risk) {
    }

    public Forecast forecast(List<PopulationEntry> entries) {
        int[] values = new int[YEARS.length];
        for (PopulationEntry entry : entries) {
            int index = indexOfYear(entry.getYearValue());
            if (index >= 0 && entry.getPopulation() != null) {
                values[index] = entry.getPopulation();
            }
        }

        double meanX = 0;
        double meanY = 0;
        for (int i = 0; i < YEARS.length; i++) {
            meanX += YEARS[i];
            meanY += values[i];
        }
        meanX /= YEARS.length;
        meanY /= YEARS.length;

        double numerator = 0;
        double denominator = 0;
        for (int i = 0; i < YEARS.length; i++) {
            numerator += (YEARS[i] - meanX) * (values[i] - meanY);
            denominator += (YEARS[i] - meanX) * (YEARS[i] - meanX);
        }

        double slope = denominator == 0 ? 0 : numerator / denominator;
        double intercept = meanY - slope * meanX;

        List<Dtos.PopulationPoint> predictions = new ArrayList<>(FUTURE_YEARS.length);
        for (int year : FUTURE_YEARS) {
            predictions.add(new Dtos.PopulationPoint(year, (int) (intercept + slope * year)));
        }

        String trend = slope > 0 ? "Increasing" : slope < 0 ? "Declining" : "Stable";
        return new Forecast(toList(values), predictions, trend, riskLevel(values[values.length - 1]));
    }

    static String riskLevel(int latest) {
        if (latest < 250) {
            return "Critical";
        }
        if (latest < 2500) {
            return "Endangered";
        }
        if (latest < 10000) {
            return "Vulnerable";
        }
        return "Stable";
    }

    private static int indexOfYear(int year) {
        for (int i = 0; i < YEARS.length; i++) {
            if (YEARS[i] == year) {
                return i;
            }
        }
        return -1;
    }

    private static List<Integer> toList(int[] values) {
        List<Integer> list = new ArrayList<>(values.length);
        for (int value : values) {
            list.add(value);
        }
        return list;
    }
}
