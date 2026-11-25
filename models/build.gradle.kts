plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("models")
    dynamicDelivery {
        // Installed together with the app, so the AI works offline from the first launch.
        deliveryType.set("install-time")
    }
}
