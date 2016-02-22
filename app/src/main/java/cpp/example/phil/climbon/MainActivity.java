package cpp.example.phil.climbon;

import android.app.ProgressDialog;
import android.location.Location;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.datasource.Feature;
import com.esri.arcgisruntime.datasource.FeatureQueryResult;
import com.esri.arcgisruntime.datasource.QueryParameters;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Map;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.CalloutStyle;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.LocationDisplay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.datasource.arcgis.ServiceFeatureTable;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {
    protected Location theLocation;
    protected Point userLocation;
    protected MapView theMapView;
    protected LocationDisplay theManager;
    private ProgressDialog progressDialog;
    private AtomicInteger count;
    private FeatureLayer areaLayer, routeLayer;
    private ServiceFeatureTable areaTable,routeTable;
    protected ArrayList<Area> areas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        areas = new ArrayList<Area>();
        final Map map = new Map(Basemap.createImagery());
        String URL = "http://services6.arcgis.com/nOUvbp8zErFpCAfI/arcgis/rest/services/Areas/FeatureServer/0";
        String routesURL =  "http://services6.arcgis.com/nOUvbp8zErFpCAfI/arcgis/rest/services/ClimbingRoutes/FeatureServer/0";
        areaTable = new ServiceFeatureTable(URL);
        routeTable = new ServiceFeatureTable(routesURL);
        routeTable.getOutFields().clear();
        routeTable.getOutFields().add("*");
        areaTable.getOutFields().clear();
        areaTable.getOutFields().add("*");

        areaLayer = new FeatureLayer(areaTable);
        routeLayer = new FeatureLayer(routeTable);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //initialize the map and the userLocationManager
        theMapView = (MapView) findViewById(R.id.map);
        theMapView.setMap(map);
        theManager = theMapView.getLocationDisplay();
        theManager.start();
        map.getOperationalLayers().add(areaLayer);


        theMapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this, theMapView) {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                // get the point that was clicked and convert it to a point in map coordinates
                final Point clickPoint = theMapView.screenToLocation(new android.graphics.Point(Math.round(e.getX()), Math.round(e.getY())));
                theMapView.getCallout().setLocation(clickPoint);
                int tolerance = 44;
                double mapTolerance = tolerance * theMapView.getUnitsPerPixel();

                // create objects required to do a selection with a query
                Envelope envelope = new Envelope(clickPoint.getX() - mapTolerance, clickPoint.getY() - mapTolerance, clickPoint.getX() + mapTolerance, clickPoint.getY() + mapTolerance, 0, 0, 0, 0, map.getSpatialReference());
                QueryParameters query = new QueryParameters();
                query.setGeometry(envelope);
                query.getOutFields().add("*");
                Callout callout = theMapView.getCallout();
                //hide the callout on a new click
                if (callout.isShowing()){
                    callout.hide();
                }
                callout.setLocation(clickPoint);

                // call select features
                final ListenableFuture<FeatureQueryResult> future = areaLayer.selectFeatures(query, FeatureLayer.SelectionMode.NEW);

                // add done loading listener to fire when the selection returns
                future.addDoneListener(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Feature selectedFeature;
                            java.util.Map<String, Object> attributes;
                            //call get on the future to get the result
                            FeatureQueryResult result = future.get();
                            if(result.iterator().hasNext()) {
                                Log.i("Test", "Feature: " + result.toString());
                                selectedFeature = result.iterator().next();
                                Log.i("Select", selectedFeature.getAttributes().toString());
                                attributes = selectedFeature.getAttributes();
                                Log.i("attributes", attributes.toString());
                                AreaBuilder builder = new AreaBuilder();
                                Area selectedArea = builder.generateArea(attributes, selectedFeature.getGeometry());
                                areaSelected(selectedArea);
                            }
                        }
                        catch (Exception e) {
                            Log.e("Error", "Select feature failed: " + e.getMessage());
                        }
                    }
                });
                return super.onSingleTapConfirmed(e);
            }
        });


        //The Button that focuses on your location, exciting.
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (theManager.isStarted()) {
                    Log.i("Location", theManager.getMapLocation().toString());
                    Log.i("Location2", theManager.getLocation().toString());
                    userLocation = theManager.getMapLocation();
                    Log.i("UserLocation", userLocation.toString());
                    if(userLocation!=null)
                        theMapView.setViewpointCenterWithScaleAsync(userLocation, 6);
                    Snackbar.make(view, "Moving to your location.", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                } else {
                    Snackbar.make(view, "Still Acquiring location. Please Wait.", Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
            }
        });
    }

    public void areaSelected(Area selectedArea){
        areas.add(selectedArea);
        showCallout(theMapView.getCallout(), selectedArea);
        theMapView.setViewpointGeometryAsync(selectedArea.getBoundries());

    }

    /**
     * This Method will display the callout when called, probably should make a method that hides it.
     *
     * @param callout
     * @param selectedArea
     */
    private void showCallout(Callout callout, final Area selectedArea) {
        LayoutInflater inflater = this.getLayoutInflater();
        int xmlid = R.xml.calloutstyle;
        View contentView = inflater.inflate(R.layout.area_callout, null);
        callout.setStyle(new CalloutStyle(this, R.xml.calloutstyle));
        callout.setContent(contentView);
        TextView name = (TextView) contentView.findViewById(R.id.name);
        TextView description =(TextView) contentView.findViewById(R.id.description);
        Button button = (Button) contentView.findViewById(R.id.zoomintoroutes);



        button.setText("Zoom In");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                zoomToRoutes(selectedArea);
            }
        });
        name.setText(selectedArea.getName());
        description.setText(selectedArea.getDescription());


        callout.show();
    }

    private void zoomToRoutes(Area selectedArea) {

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
        }

    @Override
    protected void onResume(){
        super.onResume();
        }


    @Override
    protected void onPause(){
        super.onPause();
        theManager.stop();
    }




}