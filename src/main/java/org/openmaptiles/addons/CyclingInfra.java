package org.openmaptiles.addons;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import java.util.List;
import org.openmaptiles.Layer;
import org.openmaptiles.OpenMapTilesProfile;

public class CyclingInfra implements
    Layer,
    OpenMapTilesProfile.OsmAllProcessor,
    ForwardingProfile.OsmRelationPreprocessor,
    ForwardingProfile.FeaturePostProcessor,
    OpenMapTilesProfile.IgnoreWikidata
{

  private static final String LAYER_NAME = "cycling";

  @Override
  public String name() {
    return LAYER_NAME;
  }

  // Minimal container for data we extract from OSM bicycle route relations. This is held in RAM so keep it small.
  private record RouteRelationInfo(
      // OSM ID of the relation (required):
      @Override long id,
      // Values for tags extracted from the OSM relation:
      String name, String ref
  ) implements OsmRelationInfo {}

  @Override
  public List<OsmRelationInfo> preprocessOsmRelation(OsmElement.Relation relation) {
    if (relation.hasTag("type", "route")) {
      if (relation.hasTag("route", "bicycle")) {
        return List.of(new RouteRelationInfo(
            relation.id(),
            relation.getString("name"),
            relation.getString("ref")
        ));
      }
    }
    // for any other relation, return null to ignore
    return null;
  }

  @Override
  public void processAllOsm(SourceFeature feature, FeatureCollector features) {
    if (!feature.canBeLine()) {
      return;
    }

    boolean isCycleway = feature.hasTag("highway", "cycleway");
    boolean isDesignated = feature.hasTag("bicycle", "designated") && !feature.hasTag("highway", "rest_area");
    boolean isTrack = feature.hasTag("cycleway", "track") ||
        feature.hasTag("cycleway:left", "track") ||
        feature.hasTag("cycleway:right", "track") ||
        feature.hasTag("cycleway:both", "track");
    if (isCycleway || isDesignated || isTrack) {
      features.line(LAYER_NAME)
          .setAttr("class", "cycleway")
          .setAttr("lit", feature.getTag("lit"))
          .setAttr("surface", surfaceOf(feature))
          .setAttr("smoothness", feature.getTag("smoothness"))
          // TODO: set attr for foot access/segregation
          .setMinPixelSize(0); // merge short lines in postProcess()
      return; // highest priority feature, we don't care if tags satisfy other criteria
    }

    boolean isLane = feature.hasTag("cycleway", "lane") ||
        feature.hasTag("cycleway:left", "lane") ||
        feature.hasTag("cycleway:right", "lane") ||
        feature.hasTag("cycleway:both", "lane");
    if (isLane) {
      features.line(LAYER_NAME)
          .setAttr("class", "cycle_lane")
          .setAttr("lit", feature.getTag("lit"))
          .setAttr("surface", surfaceOf(feature))
          .setAttr("smoothness", feature.getTag("smoothness"))
          .setAttr("highway", feature.getTag("highway"))
          .setMinPixelSize(0); // merge short lines in postProcess()
      return;
    }

    if (feature.hasTag("cycleway", "share_busway", "shared_lane", "opposite_lane") ||
        feature.hasTag("cycleway:left", "share_busway", "shared_lane") ||
        feature.hasTag("cycleway:right", "share_busway", "shared_lane") ||
        feature.hasTag("cycleway:both", "share_busway", "shared_lane")
    ) {
      features.line(LAYER_NAME)
          .setAttr("class", "cycle_shared")
          .setAttr("lit", feature.getTag("lit"))
          .setAttr("surface", surfaceOf(feature))
          .setAttr("smoothness", feature.getTag("smoothness"))
          .setAttr("highway", feature.getTag("highway"))
          .setMinPixelSize(0);
      return;
    }

    if (feature.hasTag("cycleway", "opposite") || feature.hasTag("oneway:bicycle", "no")) {
      features.line(LAYER_NAME)
          .setAttr("class", "cycle_no_infra")
          .setAttr("subclass", "two_way_for_bicycle")
          .setAttr("lit", feature.getTag("lit"))
          .setAttr("surface", surfaceOf(feature))
          .setAttr("smoothness", feature.getTag("smoothness"))
          .setMinPixelSize(0); // merge short lines in postProcess()
      return;
    }

    // Remaining case: no cycling infra, but is part of a cycling route
    // Get all the RouteRelationInfo instances we returned from preprocessOsmRelation that
    // this way belongs to
    // We don't need to iterate over every relation this feature belongs to, it's enough to know that the feature is
    // part of at least one cycling route
    if (!feature.relationInfo(RouteRelationInfo.class).isEmpty()) {
      features.line(LAYER_NAME)
          .setAttr("class", "cycle_no_infra")
          .setAttr("subclass", "cycle_route")
          .setAttr("lit", feature.getTag("lit"))
          .setAttr("surface", surfaceOf(feature))
          .setAttr("smoothness", feature.getTag("smoothness"))
          .setAttr("highway", feature.getTag("highway"))
          .setMinPixelSize(0);
    }
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items) throws GeometryException {
    return FeatureMerge.mergeLineStrings(items,
        0.3, // after merging, remove lines that are still less than 0.3px long
        zoom == 14 ? -1 : 0.5, // simplify output linestrings using a 0.5px tolerance, except for the highest zoomlevel
        4, // remove any detail more than 4px outside the tile boundary
        true
    );
  }

  private Object surfaceOf(SourceFeature feature) {
    return feature.getTag("cycleway:surface", feature.getTag("surface"));
  }

}
