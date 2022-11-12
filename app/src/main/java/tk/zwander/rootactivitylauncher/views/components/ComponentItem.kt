package tk.zwander.rootactivitylauncher.views.components

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tk.zwander.rootactivitylauncher.data.ComponentActionButton
import tk.zwander.rootactivitylauncher.data.component.BaseComponentInfo
import tk.zwander.rootactivitylauncher.util.isActuallyEnabled
import tk.zwander.rootactivitylauncher.views.dialogs.ComponentInfoDialog
import tk.zwander.rootactivitylauncher.views.dialogs.ExtrasDialog

@Composable
fun ComponentItem(
    forTasker: Boolean,
    component: BaseComponentInfo,
    onClick: () -> Unit,
    appEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var showingIntentOptions by rememberSaveable {
        mutableStateOf(false)
    }
    var showingComponentInfo by rememberSaveable {
        mutableStateOf(false)
    }
    var enabled by rememberSaveable {
        mutableStateOf(appEnabled)
    }

    LaunchedEffect(component.info.packageName, appEnabled) {
        enabled = withContext(Dispatchers.IO) {
            component.info.isActuallyEnabled(context)
        }
    }

    Box(
        modifier = modifier
            .then(if (forTasker) {
                Modifier.clickable {
                    onClick()
                }
            } else Modifier)
    ) {
        ComponentBar(
            icon = rememberSaveable {
                getCoilData(component)
            },
            name = component.label.toString(),
            component = component,
            whichButtons = remember(component.info.packageName) {
                arrayListOf(
                    ComponentActionButton.ComponentInfoButton(component.info) {
                        showingComponentInfo = true
                    },
                    ComponentActionButton.IntentDialogButton(component.component.flattenToString()) {
                        showingIntentOptions = true
                    },
                    ComponentActionButton.CreateShortcutButton(component),
                    ComponentActionButton.LaunchButton(component)
                )
            },
            enabled = enabled && appEnabled,
            onEnabledChanged = {
                enabled = it
            }
        )
    }

    ExtrasDialog(
        showing = showingIntentOptions,
        componentKey = component.component.flattenToString(),
        onDismissRequest = { showingIntentOptions = false }
    )

    ComponentInfoDialog(
        info = component.info,
        showing = showingComponentInfo,
        onDismissRequest = { showingComponentInfo = false }
    )
}

private fun getCoilData(data: BaseComponentInfo): Any {
    val res = data.info.iconResource.run {
        if (this == 0) data.info.applicationInfo.iconRes.run {
            if (this == 0) data.info.applicationInfo.roundIconRes
            else this
        }
        else this
    }

    return if (res != 0) {
        Uri.parse("android.resource://${data.info.packageName}/$res")
    } else {
        Uri.parse("android.resource://android/${com.android.internal.R.drawable.sym_def_app_icon}")
    }
}
