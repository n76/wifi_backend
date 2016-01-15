package org.fitchfamily.android.wifi_backend.ui;

/*
 *  WiFi Backend for Unified Network Location
 *  Copyright (C) 2014,2015  Tod Fitch
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import com.mikepenz.aboutlibraries.Libs;
import com.mikepenz.aboutlibraries.LibsBuilder;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.Extra;
import org.androidannotations.annotations.InstanceState;
import org.androidannotations.annotations.ViewById;
import org.fitchfamily.android.wifi_backend.Constants;
import org.fitchfamily.android.wifi_backend.R;

@EActivity(R.layout.activity_main)
public class MainActivity extends Activity {
    private static final int SETTINGS = 1;
    private static final int ADVANCED = 2;
    private static final int LIBRARIES = 3;
    private static final int WEBSITE = 4;

    @Extra
    protected Action action;

    @ViewById
    protected Toolbar toolbar;

    @InstanceState
    protected Bundle drawerState;

    private Drawer drawer;

    @AfterViews
    protected void init() {
        toolbar.setTitle(R.string.app_title);

        drawer = new DrawerBuilder()
                .withActivity(this)
                .withToolbar(toolbar)
                .withFireOnInitialOnClick(drawerState == null)
                .withSavedInstance(drawerState)
                .addDrawerItems(
                        new PrimaryDrawerItem()
                                .withName(R.string.drawer_settings)
                                .withIcon(GoogleMaterial.Icon.gmd_settings)
                                .withIdentifier(SETTINGS),

                        new PrimaryDrawerItem()
                                .withName(R.string.drawer_advanced)
                                .withIcon(GoogleMaterial.Icon.gmd_settings_applications)
                                .withIdentifier(ADVANCED)
                )
                .addStickyDrawerItems(
                        new PrimaryDrawerItem()
                                .withName(R.string.drawer_libraries)
                                .withIcon(GoogleMaterial.Icon.gmd_info_outline)
                                .withSelectable(false)
                                .withIdentifier(LIBRARIES),

                        new PrimaryDrawerItem()
                                .withName(R.string.drawer_website)
                                .withIcon(GoogleMaterial.Icon.gmd_info)
                                .withSelectable(false)
                                .withIdentifier(WEBSITE)
                )
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        if (drawerItem != null) {
                            final int id = drawerItem.getIdentifier();

                            if(id == SETTINGS) {
                                setFragment(new MainSettingsFragment_());
                            } else if (id == ADVANCED) {
                                setFragment(new AdvancedSettingsFragment_());
                            } else if (id == LIBRARIES) {
                                new LibsBuilder()
                                        .withFields(R.string.class.getFields())
                                        .withActivityStyle(Libs.ActivityStyle.LIGHT_DARK_TOOLBAR)
                                        .start(MainActivity.this);
                            } else if (id == WEBSITE) {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.WEBSITE)));
                            }
                        }

                        return false;
                    }
                })
                .build();

        updateTitle();

        if(action == Action.request_permission) {
            drawer.setSelection(SETTINGS);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        drawer.saveInstanceState(drawerState = new Bundle());
        super.onSaveInstanceState(outState);
    }

    private void setFragment(Fragment fragment) {
        getFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .commit();

        updateTitle();
    }

    private void updateTitle() {
        IDrawerItem item = drawer == null ? null : drawer.getDrawerItem(drawer.getCurrentSelection());

        if (item != null && item instanceof PrimaryDrawerItem) {
            toolbar.setSubtitle(((PrimaryDrawerItem) item).getName().getText(this));
        } else {
            toolbar.setSubtitle(null);
        }
    }

    @Override
    public void onBackPressed() {
        if(drawer != null && drawer.isDrawerOpen()) {
            drawer.closeDrawer();
        } else {
            super.onBackPressed();
        }
    }

    public enum Action {
        request_permission
    }
}
