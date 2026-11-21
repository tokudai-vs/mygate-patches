/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */
package app.morphe.patches.mygate.ads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceFile
import app.morphe.patcher.patch.resourceXml
import app.morphe.patcher.string
import app.morphe.patches.mygate.shared.Constants.COMPATIBILITY_MYGATE
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

// ── Native and SDUI in-app ad suppression ───────────────────────────────────────────────
private val hideAdsPatch = patch(
    name = "MyGate Hide Ads",
    description = "Short-circuits MyGate Ad SDK loaders, sinks ad network requests, and cleans up ad / bloat XML layouts.",
) {
    compatibleWith(COMPATIBILITY_MYGATE)
    execute {
        // 1. MygateAdLoader entry points — short-circuit every ad load call
        listOf(
            // loadAd() — pre-approval, allow-entries, activity-feed popup
            Fingerprint(
                definingClass = "Lcom/mygate/adsdk/MygateAdLoader;",
                name = "loadAd",
                returnType = "V",
                parameters = listOf(
                    "Landroid/widget/ImageView;",
                    "Lcom/mygate/adsdk/ScratchView;",
                    "Landroid/widget/TextView;",
                    "Landroid/widget/FrameLayout;",
                ),
            ),
            // loadGlobalMastheadAd() — visitor profile card masthead
            Fingerprint(
                definingClass = "Lcom/mygate/adsdk/MygateAdLoader;",
                name = "loadGlobalMastheadAd",
                returnType = "V",
                parameters = listOf("Landroid/widget/ImageView;"),
            ),
            // loadImageAd() — dashboard image/spotlight ad
            Fingerprint(
                definingClass = "Lcom/mygate/adsdk/MygateAdLoader;",
                name = "loadImageAd",
                returnType = "V",
                parameters = listOf("Landroid/widget/ImageView;", "I", "I"),
            ),
            // getMyGateAdData (4-param) — background ad fetch
            Fingerprint(
                definingClass = "Lcom/mygate/adsdk/MygateAdLoader;",
                name = "getMyGateAdData",
                returnType = "V",
                parameters = listOf(
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Lcom/mygate/adsdk/utils/MygateAdDataListener;",
                    "Z",
                ),
            ),
            // getMyGateAdData (7-param) — extended background ad fetch
            Fingerprint(
                definingClass = "Lcom/mygate/adsdk/MygateAdLoader;",
                name = "getMyGateAdData",
                returnType = "V",
                parameters = listOf(
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Lcom/mygate/adsdk/utils/MygateAdDataListener;",
                    "Z",
                    "Z",
                ),
            ),
            // getMyGateMultiAdData — multi-ad carousel
            Fingerprint(
                definingClass = "Lcom/mygate/adsdk/MygateAdLoader;",
                name = "getMyGateMultiAdData",
                returnType = "V",
                parameters = listOf(
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Lcom/mygate/adsdk/utils/MygateAdDataListener;",
                    "Z",
                    "Z",
                ),
            ),
            // FloatingBannerAdHandler — floating bottom-sheet banner ads
            Fingerprint(
                definingClass = "Lcom/mygate/adsdk/ads/FloatingBannerAdHandler;",
                name = "loadAd",
                returnType = "V",
                parameters = listOf("Ljava/lang/String;", "Z"),
            ),
            Fingerprint(
                definingClass = "Lcom/mygate/adsdk/ads/FloatingBannerAdHandler;",
                name = "loadViews",
                returnType = "V",
                parameters = emptyList(),
            ),
            Fingerprint(
                definingClass = "Lcom/mygate/adsdk/ads/FloatingBannerAdHandler;",
                name = "showAd",
                returnType = "V",
                parameters = emptyList(),
            ),
            // NotificationEngine approval alert / creative request -> no-op
            Fingerprint(
                definingClass = "Lcom/mygate/user/modules/notifications/engine/NotificationEngine;",
                name = "c",
                returnType = "V",
                parameters = listOf(
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                    "Ljava/lang/String;",
                ),
            ),
        ).forEach { it.method.addInstructions(0, "return-void") }

        // 2. Base ad URLs -> point to localhost sinkhole so any lingering requests fail immediately
        val sinkhole = "const-string v0, \"http://127.0.0.1:9/\"\nreturn-object v0"
        listOf("getBASE_URL", "getMETRIC_BASE_URL").forEach { name ->
            Fingerprint(
                definingClass = "Lcom/mygate/adsdk/MygateAdSdk;",
                name = name,
                returnType = "Ljava/lang/String;",
                parameters = emptyList(),
            ).method.addInstructions(0, sinkhole)
        }

        // 3. Disable ad SDK flags on Flat & UserInfo
        val returnZero = "const-string v0, \"0\"\nreturn-object v0"
        listOf(
            "Lcom/mygate/user/modules/flats/entity/Flat;" to "getHomePageEnableAdSdk",
            "Lcom/mygate/user/modules/flats/entity/Flat;" to "getNotificationEnableAdSdk",
            "Lcom/mygate/user/modules/userprofile/entity/UserInfoPojoTemp;" to "getHomePageEnableAdSdk",
            "Lcom/mygate/user/modules/userprofile/entity/UserInfoPojoTemp;" to "getNotificationEnableAdSdk",
        ).forEach { (className, methodName) ->
            Fingerprint(
                definingClass = className,
                name = methodName,
                returnType = "Ljava/lang/String;",
                parameters = emptyList(),
            ).method.addInstructions(0, returnZero)
        }

        // 4. Clean up XML ad layouts & bloat containers
        resourceXml("res/layout/advertisement.xml").use { xml ->
            xml.elements().forEach { element ->
                val id = element.attribute("android:id")
                if (id == "@id/brand_details_layout" || id == "@id/second_submit" || id == "@id/disclaimer" || id == "@id/second_layout") {
                    element.setAttribute("android:visibility", "gone")
                    element.setAttribute("android:layout_width", "0.0dp")
                    element.setAttribute("android:layout_height", "0.0dp")
                }
            }
        }
        resourceXml("res/layout/item_activity_feed_ad_image.xml").use { xml ->
            xml.elements("LinearLayout").firstOrNull()?.let {
                it.setAttribute("android:visibility", "gone")
                it.setAttribute("android:layout_width", "0.0dp")
                it.setAttribute("android:layout_height", "0.0dp")
            }
        }
        resourceXml("res/layout/item_entry_update_ad.xml").use { xml ->
            xml.elements().firstOrNull()?.let {
                it.setAttribute("android:visibility", "gone")
                it.setAttribute("android:layout_width", "0.0dp")
                it.setAttribute("android:layout_height", "0.0dp")
            }
        }

        // Hide Premium Plan Badge everywhere (VIP Crown / Ad-Supported pill)
        resourceXml("res/layout/layout_plan_badge.xml").use { xml ->
            xml.root.let { root ->
                root.setAttribute("android:visibility", "gone")
                root.setAttribute("android:layout_width", "0.0dp")
                root.setAttribute("android:layout_height", "0.0dp")
            }
            xml.elements().forEach { element ->
                element.setAttribute("android:visibility", "gone")
                element.setAttribute("android:layout_width", "0.0dp")
                element.setAttribute("android:layout_height", "0.0dp")
            }
        }

        // Hide Plan Badge from Home Flat Selection Bar
        resourceXml("res/layout/view_flat_selection.xml").use { xml ->
            xml.elements().forEach { element ->
                if (element.attribute("android:id") == "@id/l_plan_badge") {
                    element.setAttribute("android:visibility", "gone")
                    element.setAttribute("android:layout_width", "0.0dp")
                    element.setAttribute("android:layout_height", "0.0dp")
                }
            }
        }

        // Clean up Settings / Profile Activity (collapse clPlanInfo, my_plans, dividers and blank margins)
        resourceXml("res/layout/activity_settings.xml").use { xml ->
            val collapseIds = setOf(
                "@id/clPlanInfo",
                "@id/tvPlanInfo",
                "@id/tvPlanLearnMore",
                "@id/l_plan_badge_toolbar",
                "@id/chatIcon",
                "@id/gap_view_2",
                "@id/my_plans",
                "@id/subscription_icon",
                "@id/myplans_tv",
                "@id/l_plan_badge",
                "@id/ivChevron",
                "@id/userEditProfileCard",
                "@id/l_app_update_available",
            )
            xml.elements().forEach { element ->
                val id = element.attribute("android:id")
                if (id in collapseIds) {
                    element.setAttribute("android:visibility", "gone")
                    element.setAttribute("android:layout_width", "0.0dp")
                    element.setAttribute("android:layout_height", "0.0dp")
                    element.setAttribute("android:layout_marginTop", "0.0dp")
                    element.setAttribute("android:layout_marginBottom", "0.0dp")
                    element.setAttribute("android:padding", "0.0dp")
                    element.setAttribute("android:paddingHorizontal", "0.0dp")
                    element.setAttribute("android:paddingVertical", "0.0dp")
                    if (id == "@id/clPlanInfo") {
                        element.setAttribute("android:background", "@null")
                    }
                } else if (id == "@id/l_house_hold") {
                    element.setAttribute("app:layout_constraintTop_toBottomOf", "@id/userProfileCard")
                    element.setAttribute("android:layout_marginTop", "8.0dp")
                }
            }
        }

        // Clean up Settings General section (Tell a friend, Manage/Cancel subscription & re-anchor)
        resourceXml("res/layout/layout_settings_general.xml").use { xml ->
            val collapseIds = setOf(
                "@id/cl_tell_a_friend",
                "@id/tell_a_friend_icon",
                "@id/tell_a_friend_tv",
                "@id/gap_view_4",
                "@id/clManageCancelSubscription",
                "@id/ivSubscriptionIcon",
                "@id/tvManageCancelSubscription",
                "@id/gap_view_5",
            )
            xml.elements().forEach { element ->
                val id = element.attribute("android:id")
                if (id in collapseIds) {
                    element.setAttribute("android:visibility", "gone")
                    element.setAttribute("android:layout_width", "0.0dp")
                    element.setAttribute("android:layout_height", "0.0dp")
                    element.setAttribute("android:layout_marginTop", "0.0dp")
                    element.setAttribute("android:layout_marginBottom", "0.0dp")
                    element.setAttribute("android:padding", "0.0dp")
                } else if (id == "@id/cl_account_info") {
                    element.setAttribute("app:layout_constraintTop_toBottomOf", "@id/gap_view_3")
                    element.setAttribute("android:layout_marginTop", "3.0dp")
                } else if (id == "@id/divider3") {
                    element.setAttribute("android:visibility", "visible")
                    element.setAttribute("android:layout_height", "1.0dp")
                    element.setAttribute("android:layout_marginTop", "3.0dp")
                } else if (id == "@id/cl_logout") {
                    element.setAttribute("app:layout_constraintTop_toBottomOf", "@id/divider3")
                    element.setAttribute("android:layout_marginTop", "3.0dp")
                }
            }
        }

        // Clean up Bottom Navigation Bar & Expand ViewPager2 full-screen
        resourceXml("res/layout/home_activity.xml").use { xml ->
            xml.elements().forEach { element ->
                val id = element.attribute("android:id")
                if (id == "@id/bottomNavigationView" || id == "@id/btnCommunity" || id == "@id/floating_banner_ad_cl") {
                    element.setAttribute("android:visibility", "gone")
                    element.setAttribute("android:layout_width", "0.0dp")
                    element.setAttribute("android:layout_height", "0.0dp")
                } else if (id == "@id/container") {
                    element.attributeNames
                        .filter { it.endsWith("layout_constraintBottom_toTopOf") }
                        .forEach { element.removeAttribute(it) }
                    element.setAttribute("app:layout_constraintBottom_toBottomOf", "parent")
                }
            }
        }

        // Hide Community Posts Section on Home Screen
        resourceXml("res/layout/fragment_home_screen.xml").use { xml ->
            val targetIds = setOf("@id/tvCommunity", "@id/community_list_layout")
            xml.elements().forEach { element ->
                if (element.attribute("android:id") in targetIds) {
                    element.setAttribute("android:visibility", "gone")
                    element.setAttribute("android:layout_width", "0.0dp")
                    element.setAttribute("android:layout_height", "0.0dp")
                }
            }
        }
    }
}

// ── Internal: Flutter premium payload injection ───────────────────────────────────────────────
// Forces USER_PREMIUM_PLAN / SOCIETY_PREMIUM_PLAN into the JSON sent to the Flutter engine
// so the Flutter UI also sees the user as premium and eliminates feed ads.
private val flutterPremiumPatch = patch(
    name = "MyGate Flutter Premium",
    description = "Forces USER_PREMIUM_PLAN / SOCIETY_PREMIUM_PLAN into Flutter engine JSON payload to eliminate ads in Flutter feed.",
) {
    compatibleWith(COMPATIBILITY_MYGATE)
    execute {
        Fingerprint(
            definingClass = "Lcom/mygate/user/modules/userprofile/engine/UserProfileEngine;",
            name = "b",
            returnType = "V",
            parameters = listOf(
                "Lcom/mygate/user/modules/userprofile/engine/UserProfileEngine;",
                "Lorg/json/JSONObject;",
                "Z",
            ),
        ).method.addInstructions(
            0,
            """
                new-instance v0, Lorg/json/JSONObject;
                invoke-direct {v0}, Lorg/json/JSONObject;-><init>()V
                const-string v1, "status"
                const-string v2, "ACTIVE"
                invoke-virtual {v0, v1, v2}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;
                const-string v1, "code"
                const-string v2, "USER_PREMIUM_PLAN"
                invoke-virtual {v0, v1, v2}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;
                const-string v1, "name"
                const-string v2, "Premium"
                invoke-virtual {v0, v1, v2}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;
                const-string v1, "user_plan"
                invoke-virtual {p1, v1, v0}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;
                new-instance v0, Lorg/json/JSONObject;
                invoke-direct {v0}, Lorg/json/JSONObject;-><init>()V
                const-string v1, "status"
                const-string v2, "ACTIVE"
                invoke-virtual {v0, v1, v2}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;
                const-string v1, "code"
                const-string v2, "SOCIETY_PREMIUM_PLAN"
                invoke-virtual {v0, v1, v2}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;
                const-string v1, "name"
                const-string v2, "Premium"
                invoke-virtual {v0, v1, v2}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;
                const-string v1, "sort_index"
                const/4 v2, 0x3
                invoke-virtual {v0, v1, v2}, Lorg/json/JSONObject;->put(Ljava/lang/String;I)Lorg/json/JSONObject;
                const-string v1, "society_highest_plan"
                invoke-virtual {p1, v1, v0}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;
            """,
        )
    }
}

// ── Internal: Flutter binary string sinkhole in libapp.so ───────────────────────────────────
private val blockFlutterAdsPatch = patch(
    name = "MyGate Block Flutter Ads",
    description = "Removes home feed advertisements and sponsored community notice cards via binary sinkholing in Flutter libapp.so.",
) {
    compatibleWith(COMPATIBILITY_MYGATE)
    execute {
        val replacements = listOf(
            // --- Endpoints & Image CDN Sinkholes ---
            "https://cmp.mygate.com/".toByteArray(Charsets.UTF_8) to "http://127.0.0.1:9099//".toByteArray(Charsets.UTF_8),
            "https://classifieds-be.mygate.com/".toByteArray(Charsets.UTF_8) to "http://127.0.0.1:9099/blocked-ads/".toByteArray(Charsets.UTF_8),
            "https://cmp.mint-01.mgmaglev.xyz/".toByteArray(Charsets.UTF_8) to "http://127.0.0.1:9099/blocked/xyz".toByteArray(Charsets.UTF_8),
            "https://trill.mygate.com/".toByteArray(Charsets.UTF_8) to "http://127.0.0.1:9099////".toByteArray(Charsets.UTF_8),
            "/digital/campaign/".toByteArray(Charsets.UTF_8) to "/blocked/campaign/".toByteArray(Charsets.UTF_8),
            "/metrics/capture/campaign".toByteArray(Charsets.UTF_8) to "/blocked/capture/campaign".toByteArray(Charsets.UTF_8),

            // --- Feed Notice & Marketing Platform Sinkhole ---
            "mobile/noticeListing".toByteArray(Charsets.UTF_8) to "mobile/blockedNotice".toByteArray(Charsets.UTF_8),

            // --- Spotlight Dynamic Widget Ads & Staging Metrics Sinkholes ---
            "/spotlight".toByteArray(Charsets.UTF_8) to "/blocklite".toByteArray(Charsets.UTF_8),
            "https://metrics.kappa.mgmaglev.xyz/".toByteArray(Charsets.UTF_8) to "http://127.0.0.1:9099/blocked/abcd/".toByteArray(Charsets.UTF_8),

            // --- Logical Plan & Tier Ad Banners ---
            "SOCIETY_FREE_PLAN".toByteArray(Charsets.UTF_8) to "SOCIETY_DISA_PLAN".toByteArray(Charsets.UTF_8),
            "shouldShowAdTierBanner".toByteArray(Charsets.UTF_8) to "shouldHideAdTierBanner".toByteArray(Charsets.UTF_8),
            "ad_tag_enabled".toByteArray(Charsets.UTF_8) to "ad_tag_disable".toByteArray(Charsets.UTF_8),

            // --- Feed Sponsored Notice Asset Neutralization ---
            "assets/images/svg/common/ic_notice_yellow.svg".toByteArray(Charsets.UTF_8) to "assets/images/svg/common/ic_notice_blockd.svg".toByteArray(Charsets.UTF_8),
        )

        listOf("lib/arm64-v8a/libapp.so", "lib/armeabi-v7a/libapp.so").forEach { entry ->
            try {
                val file = resourceFile(entry)
                if (file.exists()) {
                    val bytes = file.readBytes()
                    var modified = false
                    val newBytes = bytes.clone()

                    for ((target, replacement) in replacements) {
                        for (i in 0..(newBytes.size - target.size)) {
                            var match = true
                            for (j in target.indices) {
                                if (newBytes[i + j] != target[j]) {
                                    match = false
                                    break
                                }
                            }
                            if (match) {
                                for (j in replacement.indices) {
                                    newBytes[i + j] = replacement[j]
                                }
                                modified = true
                            }
                        }
                    }

                    if (modified) {
                        file.writeBytes(newBytes)
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Neutralize Flutter home header VIP crown icon asset
        try {
            val emptySvg = """<svg width="0" height="0" viewBox="0 0 0 0" fill="none" xmlns="http://www.w3.org/2000/svg"/>""".toByteArray(Charsets.UTF_8)
            val premiumUserSvg = resourceFile("assets/flutter_assets/assets/images/svg/common/ic_premium_user.svg")
            if (premiumUserSvg.exists()) {
                premiumUserSvg.writeBytes(emptySvg)
            }
        } catch (_: Exception) {
        }
    }
}

// ── Public MyGate Patch: Ad-Free, UI Bloat Cleanup & Resident Limits ────────────────────
@Suppress("unused")
val myGatePatch = patch(
    name = "MyGate ad-free & UI cleanup",
    description = "Removes all banner ads, sponsored cards, bottom navigation bar, crown icons, and bloat widgets.",
) {
    compatibleWith(COMPATIBILITY_MYGATE)
    dependsOn(hideAdsPatch, flutterPremiumPatch, blockFlutterAdsPatch)

    execute {
        // ── 1. Neutralize Plan Badge binding (removes VIP crown & ad-supported badge) ────
        Fingerprint(
            definingClass = "Lcom/mygate/user/utilities/KotlinUtils\$Companion;",
            name = "M",
            returnType = "V",
            parameters = listOf(
                "Lcom/mygate/user/databinding/LayoutPlanBadgeBinding;",
                "Z",
            ),
        ).method.addInstructions(
            0,
            """
                if-eqz p0, :cond_badge_end
                iget-object v0, p0, Lcom/mygate/user/databinding/LayoutPlanBadgeBinding;->a:Landroidx/constraintlayout/widget/ConstraintLayout;
                if-eqz v0, :cond_badge_end
                invoke-static {v0}, Lcom/mygate/user/common/extensions/ViewExtensionsKt;->j(Landroid/view/View;)V
                :cond_badge_end
                return-void
            """,
        )

        // ── 2. Filter Quick Actions in HomeScreenViewModel.b(ArrayList) ───────────────
        // Strips: Community, Feed, Smart Devices, Marketplace, Settings
        Fingerprint(
            definingClass = "Lcom/mygate/user/modules/dashboard/ui/viewmodels/HomeScreenViewModel;",
            name = "b",
            returnType = "V",
            parameters = listOf("Ljava/util/ArrayList;"),
        ).method.addInstructions(
            0,
            """
                if-eqz p0, :cond_vm_filter_end
                invoke-virtual {p0}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;
                move-result-object v0
                :cond_vm_loop
                invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z
                move-result v1
                if-eqz v1, :cond_vm_filter_end
                invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;
                move-result-object v1
                check-cast v1, Lcom/mygate/user/modules/dashboard/entity/Widgets;
                if-eqz v1, :cond_vm_loop

                invoke-virtual {v1}, Lcom/mygate/user/modules/dashboard/entity/Widgets;->getTitle()Ljava/lang/String;
                move-result-object v2
                if-eqz v2, :cond_vm_check_action
                const-string v3, "(?i).*(community|feed|smart\\s*device|marketplace|setting).*"
                invoke-virtual {v2, v3}, Ljava/lang/String;->matches(Ljava/lang/String;)Z
                move-result v2
                if-eqz v2, :cond_vm_check_action
                invoke-interface {v0}, Ljava/util/Iterator;->remove()V
                goto :cond_vm_loop

                :cond_vm_check_action
                invoke-virtual {v1}, Lcom/mygate/user/modules/dashboard/entity/Widgets;->getAction()Lcom/mygate/user/modules/dashboard/entity/LaunchPadAction;
                move-result-object v1
                if-eqz v1, :cond_vm_loop

                invoke-virtual {v1}, Lcom/mygate/user/modules/dashboard/entity/LaunchPadAction;->getAction_name()Ljava/lang/String;
                move-result-object v2
                if-eqz v2, :cond_vm_check_action_id
                const-string v3, "(?i).*(community|feed|smart\\s*device|marketplace|setting).*"
                invoke-virtual {v2, v3}, Ljava/lang/String;->matches(Ljava/lang/String;)Z
                move-result v2
                if-eqz v2, :cond_vm_check_action_id
                invoke-interface {v0}, Ljava/util/Iterator;->remove()V
                goto :cond_vm_loop

                :cond_vm_check_action_id
                invoke-virtual {v1}, Lcom/mygate/user/modules/dashboard/entity/LaunchPadAction;->getAction_id()Ljava/lang/String;
                move-result-object v2
                if-eqz v2, :cond_vm_loop
                const-string v3, "(?i).*(community|feed|smart\\s*device|marketplace|setting).*"
                invoke-virtual {v2, v3}, Ljava/lang/String;->matches(Ljava/lang/String;)Z
                move-result v2
                if-eqz v2, :cond_vm_loop
                invoke-interface {v0}, Ljava/util/Iterator;->remove()V
                goto :cond_vm_loop

                :cond_vm_filter_end
            """,
        )

        // ── 3. Filter Quick Actions in HomeScreenFragment.t0(HomeScreenFragment, ArrayList) ─
        Fingerprint(
            definingClass = "Lcom/mygate/user/modules/dashboard/ui/fragments/HomeScreenFragment;",
            name = "t0",
            returnType = "V",
            parameters = listOf(
                "Lcom/mygate/user/modules/dashboard/ui/fragments/HomeScreenFragment;",
                "Ljava/util/ArrayList;",
            ),
        ).method.addInstructions(
            0,
            """
                if-eqz p1, :cond_frag_filter_end
                invoke-virtual {p1}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;
                move-result-object v0
                :cond_frag_loop
                invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z
                move-result v1
                if-eqz v1, :cond_frag_filter_end
                invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;
                move-result-object v1
                check-cast v1, Lcom/mygate/user/modules/dashboard/entity/Widgets;
                if-eqz v1, :cond_frag_loop

                invoke-virtual {v1}, Lcom/mygate/user/modules/dashboard/entity/Widgets;->getTitle()Ljava/lang/String;
                move-result-object v2
                if-eqz v2, :cond_frag_check_action
                const-string v3, "(?i).*(community|feed|smart\\s*device|marketplace|setting).*"
                invoke-virtual {v2, v3}, Ljava/lang/String;->matches(Ljava/lang/String;)Z
                move-result v2
                if-eqz v2, :cond_frag_check_action
                invoke-interface {v0}, Ljava/util/Iterator;->remove()V
                goto :cond_frag_loop

                :cond_frag_check_action
                invoke-virtual {v1}, Lcom/mygate/user/modules/dashboard/entity/Widgets;->getAction()Lcom/mygate/user/modules/dashboard/entity/LaunchPadAction;
                move-result-object v1
                if-eqz v1, :cond_frag_loop

                invoke-virtual {v1}, Lcom/mygate/user/modules/dashboard/entity/LaunchPadAction;->getAction_name()Ljava/lang/String;
                move-result-object v2
                if-eqz v2, :cond_frag_check_action_id
                const-string v3, "(?i).*(community|feed|smart\\s*device|marketplace|setting).*"
                invoke-virtual {v2, v3}, Ljava/lang/String;->matches(Ljava/lang/String;)Z
                move-result v2
                if-eqz v2, :cond_frag_check_action_id
                invoke-interface {v0}, Ljava/util/Iterator;->remove()V
                goto :cond_frag_loop

                :cond_frag_check_action_id
                invoke-virtual {v1}, Lcom/mygate/user/modules/dashboard/entity/LaunchPadAction;->getAction_id()Ljava/lang/String;
                move-result-object v2
                if-eqz v2, :cond_frag_loop
                const-string v3, "(?i).*(community|feed|smart\\s*device|marketplace|setting).*"
                invoke-virtual {v2, v3}, Ljava/lang/String;->matches(Ljava/lang/String;)Z
                move-result v2
                if-eqz v2, :cond_frag_loop
                invoke-interface {v0}, Ljava/util/Iterator;->remove()V
                goto :cond_frag_loop

                :cond_frag_filter_end
            """,
        )

        // ── 4. KotlinUtils.showUpgradePremiumDialog() -> no-op ───────────────────────
        Fingerprint(
            definingClass = "Lcom/mygate/user/utilities/KotlinUtils\$Companion;",
            returnType = "V",
            filters = listOf(
                methodCall(name = "show", returnType = "V", parameters = emptyList()),
            ),
            custom = { method, _ -> method.parameters.size == 1 },
        ).method.addInstructions(0, "return-void")

        // ── 5. UpgradePlanFragment plan-status gate ───────────────────────────────────
        Fingerprint(
            definingClass = "Lcom/mygate/user/modules/userprofile/ui/UpgradePlanFragment;",
            returnType = "V",
            filters = listOf(
                string("USER_PREMIUM_PLAN"),
                string("SOCIETY_PREMIUM_PLAN"),
            ),
        ).let { fingerprint ->
            val method = fingerprint.method
            fingerprint.instructionMatches.reversed().forEach { match ->
                val instructions = method.instructions
                var moveResultIndex = -1
                var moveResultRegister = -1
                for (i in match.index + 1 until instructions.size) {
                    val instruction = instructions[i]
                    if (instruction.opcode == Opcode.MOVE_RESULT) {
                        moveResultIndex = i
                        moveResultRegister = (instruction as OneRegisterInstruction).registerA
                        break
                    }
                }
                if (moveResultIndex >= 0) {
                    method.addInstructions(
                        moveResultIndex + 1,
                        "const/4 v$moveResultRegister, 0x1",
                    )
                }
            }
        }

        // ── 6. Restored: Core Gatekeeper KotlinUtils.isPremiumUser() -> true ──────────
        Fingerprint(
            definingClass = "Lcom/mygate/user/utilities/KotlinUtils\$Companion;",
            returnType = "Z",
            parameters = emptyList(),
            strings = listOf("USER_PREMIUM_PLAN", "SOCIETY_PREMIUM_PLAN"),
        ).method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")

        // ── 7. Restored: UserProfile plan resolvers ───────────────────────────────────
        Fingerprint(
            definingClass = "Lcom/mygate/user/modules/userprofile/entity/UserProfile;",
            name = "getCombinedUserPlanStatus",
            returnType = "Ljava/lang/String;",
            parameters = emptyList(),
        ).method.addInstructions(0, "const-string v0, \"SOCIETY_PREMIUM_PLAN\"\nreturn-object v0")

        Fingerprint(
            definingClass = "Lcom/mygate/user/modules/userprofile/entity/UserProfile;",
            name = "getAdTierLabel",
            returnType = "Ljava/lang/String;",
            parameters = emptyList(),
        ).method.addInstructions(0, "const-string v0, \"Premium\"\nreturn-object v0")

        Fingerprint(
            definingClass = "Lcom/mygate/user/modules/userprofile/entity/UserProfile;",
            name = "getUserPlan",
            returnType = "Lcom/mygate/user/modules/flats/entity/SocietyPlan;",
            parameters = emptyList(),
        ).method.addInstructions(
            0,
            """
                iget-object v0, p0, Lcom/mygate/user/modules/userprofile/entity/UserProfile;->userPlan:Lcom/mygate/user/modules/flats/entity/SocietyPlan;
                if-nez v0, :cond_has_user_plan
                new-instance v0, Lcom/mygate/user/modules/flats/entity/SocietyPlan;
                invoke-direct {v0}, Lcom/mygate/user/modules/flats/entity/SocietyPlan;-><init>()V
                iput-object v0, p0, Lcom/mygate/user/modules/userprofile/entity/UserProfile;->userPlan:Lcom/mygate/user/modules/flats/entity/SocietyPlan;
                :cond_has_user_plan
                return-object v0
            """,
        )

        Fingerprint(
            definingClass = "Lcom/mygate/user/modules/userprofile/entity/UserProfile;",
            name = "getSocietyHighestPlan",
            returnType = "Lcom/mygate/user/modules/flats/entity/SocietyPlan;",
            parameters = emptyList(),
        ).method.addInstructions(
            0,
            """
                iget-object v0, p0, Lcom/mygate/user/modules/userprofile/entity/UserProfile;->societyHighestPlan:Lcom/mygate/user/modules/flats/entity/SocietyPlan;
                if-nez v0, :cond_has_soc_plan
                new-instance v0, Lcom/mygate/user/modules/flats/entity/SocietyPlan;
                invoke-direct {v0}, Lcom/mygate/user/modules/flats/entity/SocietyPlan;-><init>()V
                iput-object v0, p0, Lcom/mygate/user/modules/userprofile/entity/UserProfile;->societyHighestPlan:Lcom/mygate/user/modules/flats/entity/SocietyPlan;
                :cond_has_soc_plan
                return-object v0
            """,
        )

        // ── 8. Restored: SocietyPlan getters ──────────────────────────────────────────
        Fingerprint(
            definingClass = "Lcom/mygate/user/modules/flats/entity/SocietyPlan;",
            name = "getCode",
            returnType = "Ljava/lang/String;",
            parameters = emptyList(),
        ).method.addInstructions(0, "const-string v0, \"SOCIETY_PREMIUM_PLAN\"\nreturn-object v0")

        Fingerprint(
            definingClass = "Lcom/mygate/user/modules/flats/entity/SocietyPlan;",
            name = "getName",
            returnType = "Ljava/lang/String;",
            parameters = emptyList(),
        ).method.addInstructions(0, "const-string v0, \"Premium Plan\"\nreturn-object v0")

        // ── 9. Restored: Flat.getProductPackage() & getSocietyPlan() ──────────────────
        Fingerprint(
            definingClass = "Lcom/mygate/user/modules/flats/entity/Flat;",
            name = "getProductPackage",
            returnType = "Ljava/lang/String;",
            parameters = emptyList(),
        ).method.addInstructions(0, "const-string v0, \"PREMIUM\"\nreturn-object v0")

        Fingerprint(
            definingClass = "Lcom/mygate/user/modules/flats/entity/Flat;",
            name = "getSocietyPlan",
            returnType = "Lcom/mygate/user/modules/flats/entity/SocietyPlan;",
            parameters = emptyList(),
        ).method.addInstructions(
            0,
            """
                iget-object v0, p0, Lcom/mygate/user/modules/flats/entity/Flat;->societyPlan:Lcom/mygate/user/modules/flats/entity/SocietyPlan;
                if-nez v0, :cond_has_flat_soc_plan
                new-instance v0, Lcom/mygate/user/modules/flats/entity/SocietyPlan;
                invoke-direct {v0}, Lcom/mygate/user/modules/flats/entity/SocietyPlan;-><init>()V
                iput-object v0, p0, Lcom/mygate/user/modules/flats/entity/Flat;->societyPlan:Lcom/mygate/user/modules/flats/entity/SocietyPlan;
                :cond_has_flat_soc_plan
                return-object v0
            """,
        )

        // ── 10. Restored: UserInfoPojoTemp getters ────────────────────────────────────
        Fingerprint(
            definingClass = "Lcom/mygate/user/modules/userprofile/entity/UserInfoPojoTemp;",
            name = "getAdTierLabel",
            returnType = "Ljava/lang/String;",
            parameters = emptyList(),
        ).method.addInstructions(0, "const-string v0, \"Premium\"\nreturn-object v0")

        Fingerprint(
            definingClass = "Lcom/mygate/user/modules/userprofile/entity/UserInfoPojoTemp;",
            name = "getProductPackage",
            returnType = "Ljava/lang/String;",
            parameters = emptyList(),
        ).method.addInstructions(0, "const-string v0, \"PREMIUM\"\nreturn-object v0")

        Fingerprint(
            definingClass = "Lcom/mygate/user/modules/userprofile/entity/UserInfoPojoTemp;",
            name = "getSocietyPlan",
            returnType = "Lcom/mygate/user/modules/flats/entity/SocietyPlan;",
            parameters = emptyList(),
        ).method.addInstructions(
            0,
            """
                iget-object v0, p0, Lcom/mygate/user/modules/userprofile/entity/UserInfoPojoTemp;->societyPlan:Lcom/mygate/user/modules/flats/entity/SocietyPlan;
                if-nez v0, :cond_has_temp_soc_plan
                new-instance v0, Lcom/mygate/user/modules/flats/entity/SocietyPlan;
                invoke-direct {v0}, Lcom/mygate/user/modules/flats/entity/SocietyPlan;-><init>()V
                iput-object v0, p0, Lcom/mygate/user/modules/userprofile/entity/UserInfoPojoTemp;->societyPlan:Lcom/mygate/user/modules/flats/entity/SocietyPlan;
                :cond_has_temp_soc_plan
                return-object v0
            """,
        )

        Fingerprint(
            definingClass = "Lcom/mygate/user/modules/userprofile/entity/UserInfoPojoTemp;",
            name = "getUserPlan",
            returnType = "Lcom/mygate/user/modules/flats/entity/SocietyPlan;",
            parameters = emptyList(),
        ).method.addInstructions(
            0,
            """
                iget-object v0, p0, Lcom/mygate/user/modules/userprofile/entity/UserInfoPojoTemp;->userPlan:Lcom/mygate/user/modules/flats/entity/SocietyPlan;
                if-nez v0, :cond_has_temp_user_plan
                new-instance v0, Lcom/mygate/user/modules/flats/entity/SocietyPlan;
                invoke-direct {v0}, Lcom/mygate/user/modules/flats/entity/SocietyPlan;-><init>()V
                iput-object v0, p0, Lcom/mygate/user/modules/userprofile/entity/UserInfoPojoTemp;->userPlan:Lcom/mygate/user/modules/flats/entity/SocietyPlan;
                :cond_has_temp_user_plan
                return-object v0
            """,
        )

        Fingerprint(
            definingClass = "Lcom/mygate/user/modules/userprofile/entity/UserInfoPojoTemp;",
            name = "getSocietyHighestPlan",
            returnType = "Lcom/mygate/user/modules/flats/entity/SocietyPlan;",
            parameters = emptyList(),
        ).method.addInstructions(
            0,
            """
                iget-object v0, p0, Lcom/mygate/user/modules/userprofile/entity/UserInfoPojoTemp;->societyHighestPlan:Lcom/mygate/user/modules/flats/entity/SocietyPlan;
                if-nez v0, :cond_has_temp_high_plan
                new-instance v0, Lcom/mygate/user/modules/flats/entity/SocietyPlan;
                invoke-direct {v0}, Lcom/mygate/user/modules/flats/entity/SocietyPlan;-><init>()V
                iput-object v0, p0, Lcom/mygate/user/modules/userprofile/entity/UserInfoPojoTemp;->societyHighestPlan:Lcom/mygate/user/modules/flats/entity/SocietyPlan;
                :cond_has_temp_high_plan
                return-object v0
            """,
        )

        // ── 11. UserProfile.getPremiumFeatureList() — ensure non-null ─────────────────
        Fingerprint(
            definingClass = "Lcom/mygate/user/modules/userprofile/entity/UserProfile;",
            name = "getPremiumFeatureList",
            returnType = "Lcom/mygate/user/modules/userprofile/entity/PremiumFeatureCategory;",
            parameters = emptyList(),
        ).method.addInstructions(
            0,
            """
                iget-object v0, p0, Lcom/mygate/user/modules/userprofile/entity/UserProfile;->premiumFeatureCategory:Lcom/mygate/user/modules/userprofile/entity/PremiumFeatureCategory;
                if-nez v0, :cond_0
                new-instance v0, Lcom/mygate/user/modules/userprofile/entity/PremiumFeatureCategory;
                invoke-direct {v0}, Lcom/mygate/user/modules/userprofile/entity/PremiumFeatureCategory;-><init>()V
                iput-object v0, p0, Lcom/mygate/user/modules/userprofile/entity/UserProfile;->premiumFeatureCategory:Lcom/mygate/user/modules/userprofile/entity/PremiumFeatureCategory;
                :cond_0
                return-object v0
            """,
        )

        // ── 12. PremiumFeatureCategory getters — force all limits to 1 ────────────────
        val returnOne = "const/4 v0, 0x1\ninvoke-static {v0}, Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;\nmove-result-object v0\nreturn-object v0"
        listOf("getFreqDelivery", "getSurpriseDelivery", "getFreqCab", "getFreqVisitingHelp", "getFreqGuest").forEach { name ->
            Fingerprint(
                definingClass = "Lcom/mygate/user/modules/userprofile/entity/PremiumFeatureCategory;",
                name = name,
                returnType = "Ljava/lang/Integer;",
                parameters = emptyList(),
            ).method.addInstructions(0, returnOne)
        }
    }
}
