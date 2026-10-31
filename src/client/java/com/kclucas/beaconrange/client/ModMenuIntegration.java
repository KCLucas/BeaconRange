package com.kclucas.beaconrange.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Wird nur geladen, wenn Mod Menu installiert ist; dann erscheint dort der Einstellungs-Knopf. */
public class ModMenuIntegration implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return BeaconRangeConfigScreen::new;
	}
}
