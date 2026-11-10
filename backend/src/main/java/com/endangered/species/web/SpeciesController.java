package com.endangered.species.web;

import com.endangered.species.service.SpeciesCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Search, explore, analysis and detection read models. */
@RestController
public class SpeciesController {

    private final SpeciesCatalogService catalog;

    public SpeciesController(SpeciesCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/search/{animal}")
    public List<Dtos.SpeciesSummary> search(@PathVariable String animal) {
        return catalog.search(animal);
    }

    /**
     * Under {@code /api} because {@code /animal/{region}/{category}/{state}/{animal}} is also
     * a page of the React app.
     */
    @GetMapping("/api/animal/{region}/{category}/{state}/{animal}")
    public List<Dtos.SpeciesSummary> animal(@PathVariable String region, @PathVariable String category,
                                            @PathVariable String state, @PathVariable String animal) {
        return catalog.explore(region, category, state, animal);
    }

    @GetMapping("/analysis/{region}/{category}/{state}/{animal}")
    public Object analysis(@PathVariable String region, @PathVariable String category,
                           @PathVariable String state, @PathVariable String animal) {
        return catalog.analysis(region, category, state, animal);
    }

    @GetMapping("/detection/{animal}")
    public Object detection(@PathVariable String animal) {
        return catalog.detection(animal);
    }

    @GetMapping("/detection/{animal}/{regionType}/{location}")
    public Object detectionAt(@PathVariable String animal, @PathVariable String regionType,
                              @PathVariable String location) {
        return catalog.detectionAt(animal, regionType, location);
    }

    @GetMapping("/detection-locations/{animal}/{regionType}")
    public List<String> detectionLocations(@PathVariable String animal, @PathVariable String regionType) {
        return catalog.locations(animal, regionType);
    }
}
