package com.dualweathertemp.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dualweathertemp.app.BuildConfig
import com.dualweathertemp.app.R
import com.dualweathertemp.app.data.AppSettings
import com.dualweathertemp.app.data.BackgroundStyle
import com.dualweathertemp.app.data.UnitOrder
import com.dualweathertemp.app.ui.sections.GlassCard
import com.dualweathertemp.app.ui.sections.ScreenTopBar
import com.dualweathertemp.app.ui.sections.SegmentedChoice
import com.dualweathertemp.app.ui.sections.secondaryContentColor

@Composable
fun SettingsScreen(
    settings: AppSettings,
    notificationsBlocked: Boolean,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenTopBar(title = stringResource(R.string.settings), onBack = onBack)
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            SettingCard(stringResource(R.string.settings_unit_order)) {
                val orders = UnitOrder.entries
                SegmentedChoice(
                    options = listOf(stringResource(R.string.settings_f_first), stringResource(R.string.settings_c_first)),
                    selectedIndex = orders.indexOf(settings.unitOrder),
                    onSelect = { index -> onChange { it.copy(unitOrder = orders[index]) } },
                )
            }
            SettingCard(stringResource(R.string.settings_widget_refresh)) {
                val choices = AppSettings.WIDGET_REFRESH_CHOICES
                SegmentedChoice(
                    options = choices.map { refreshLabel(it) },
                    selectedIndex = choices.indexOf(settings.widgetRefreshMinutes),
                    onSelect = { index -> onChange { it.copy(widgetRefreshMinutes = choices[index]) } },
                )
            }
            SettingCard(stringResource(R.string.settings_background)) {
                val styles = BackgroundStyle.entries
                SegmentedChoice(
                    options = listOf(
                        stringResource(R.string.settings_bg_sky),
                        stringResource(R.string.settings_bg_light),
                        stringResource(R.string.settings_bg_dark),
                    ),
                    selectedIndex = styles.indexOf(settings.background),
                    onSelect = { index -> onChange { it.copy(background = styles[index]) } },
                )
            }
            SwitchCard(
                title = stringResource(R.string.settings_alerts),
                detail = stringResource(R.string.settings_alerts_detail),
                checked = settings.alertsEnabled,
                onCheckedChange = { checked -> onChange { it.copy(alertsEnabled = checked) } },
            ) {
                if (settings.alertsEnabled && notificationsBlocked) {
                    Text(
                        text = stringResource(R.string.notifications_blocked),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.turn_on), color = LocalPalette.current.content)
                    }
                }
            }
            SwitchCard(
                title = stringResource(R.string.settings_alerts_saved),
                detail = stringResource(
                    if (settings.alertsForSavedPlaces) R.string.settings_alerts_saved_on else R.string.settings_alerts_saved_off,
                ),
                checked = settings.alertsForSavedPlaces,
                enabled = settings.alertsEnabled,
                onCheckedChange = { checked -> onChange { it.copy(alertsForSavedPlaces = checked) } },
            )

            Text(
                text = stringResource(R.string.about_data) + "\n" + stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryContentColor(),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
            )
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun refreshLabel(minutes: Int): String =
    if (minutes < 60) stringResource(R.string.settings_minutes, minutes)
    else pluralStringResource(R.plurals.settings_hours, minutes / 60, minutes / 60)

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    GlassCard(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun SwitchCard(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    extra: @Composable () -> Unit = {},
) {
    GlassCard(modifier = Modifier.padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = secondaryContentColor())
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
        extra()
    }
}
