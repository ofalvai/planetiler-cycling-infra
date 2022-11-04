package org.openmaptiles.addons;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.reader.SourceFeature;
import org.openmaptiles.Layer;
import org.openmaptiles.OpenMapTilesProfile;

public class CyclingPoi implements Layer, OpenMapTilesProfile.OsmAllProcessor {

  private static final String LAYER_NAME = "cycling_poi";

  @Override
  public String name() {
    return LAYER_NAME;
  }

  @Override
  public void processAllOsm(SourceFeature feature, FeatureCollector features) {
    if (feature.hasTag("amenity", "drinking_water")) {
      features.centroidIfConvex(LAYER_NAME)
          .setMinZoom(14)
          .setAttr("class", "drinking_water");
    }

    if (feature.hasTag("amenity", "bicycle_repair_station")) {
      features.centroidIfConvex(LAYER_NAME)
          .setMinZoom(13)
          .setAttr("class", "bicycle_repair_station");
    }
  }
}
