package com.robert.permissions

import android.R
import android.annotation.TargetApi
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import java.util.*

/**
 * Created by Robert on 2020-Dec-15
 */
@TargetApi(Build.VERSION_CODES.M)
class PermissionsActivity : Activity() {
    private var allPermissions: ArrayList<String>? = null
    private var deniedPermissions: ArrayList<String>? = null
    private var noRationaleList: ArrayList<String>? = null
    private var options: Permissions.Options? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFinishOnTouchOutside(false)
        val intent = intent
        if (intent == null || !intent.hasExtra(EXTRA_PERMISSIONS)) {
            finish()
            return
        }
        window.statusBarColor = 0
        allPermissions = intent.getStringArrayListExtra(EXTRA_PERMISSIONS)
        options = intent.getSerializableExtra(EXTRA_OPTIONS) as Permissions.Options?
        if (options == null) {
            options = Permissions.Options()
        }
        deniedPermissions = ArrayList()
        noRationaleList = ArrayList()
        var noRationale = true
        allPermissions?.let { permissions ->
            for (permission in permissions) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions!!.add(permission)
                    if (shouldShowRequestPermissionRationale(permission)) {
                        noRationale = false
                    } else {
                        noRationaleList!!.add(permission)
                    }
                }
            }
        }

        if (deniedPermissions!!.isEmpty()) {
            grant()
            return
        }
        val rationale = intent.getStringExtra(EXTRA_RATIONALE)
        if (noRationale || TextUtils.isEmpty(rationale)) {
            Permissions.log("No rationale.")
            deniedPermissions?.let {
                requestPermissions(it.toTypedArray(), RC_PERMISSION)
            }
        } else {
            Permissions.log("Show rationale.")
            showRationale(rationale)
        }
    }

    private fun showRationale(rationale: String?) {
        val listener = DialogInterface.OnClickListener { dialog, which ->
            if (which == DialogInterface.BUTTON_POSITIVE) {
                deniedPermissions?.let {
                    requestPermissions(it.toTypedArray(), RC_PERMISSION)
                }
            } else {
                deny()
            }
        }
        AlertDialog.Builder(this).setTitle(options!!.rationaleDialogTitle)
                .setMessage(rationale)
                .setPositiveButton(R.string.ok, listener)
                .setNegativeButton(R.string.cancel, listener)
                .setOnCancelListener { deny() }.create().show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (grantResults.isEmpty()) {
            deny()
        } else {
            deniedPermissions!!.clear()
            for (i in grantResults.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions!!.add(permissions[i])
                }
            }
            if (deniedPermissions!!.size == 0) {
                Permissions.log("Just allowed.")
                grant()
            } else {
                val blockedList = ArrayList<String?>() //set not to ask again.
                val justBlockedList = ArrayList<String>() //just set not to ask again.
                val justDeniedList = ArrayList<String>()
                for (permission in deniedPermissions!!) {
                    if (shouldShowRequestPermissionRationale(permission)) {
                        justDeniedList.add(permission)
                    } else {
                        blockedList.add(permission)
                        if (!noRationaleList!!.contains(permission)) {
                            justBlockedList.add(permission)
                        }
                    }
                }
                if (justBlockedList.size > 0) { //checked don't ask again for at least one.
                    val permissionHandler = permissionHandler
                    finish()
                    permissionHandler?.onJustBlocked(applicationContext, justBlockedList, deniedPermissions)
                } else if (justDeniedList.size > 0) { //clicked deny for at least one.
                    deny()
                } else { //unavailable permissions were already set not to ask again.
                    if (permissionHandler != null && !permissionHandler!!.onBlocked(applicationContext, blockedList)) {
                        sendToSettings()
                    } else finish()
                }
            }
        }
    }

    private fun sendToSettings() {
        if (!options!!.sendBlockedToSettings) {
            deny()
            return
        }
        Permissions.log("Ask to go to settings.")
        AlertDialog.Builder(this).setTitle(options!!.settingsDialogTitle)
                .setMessage(options!!.settingsDialogMessage)
                .setPositiveButton(options!!.settingsText) { dialog, which ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                    startActivityForResult(intent, RC_SETTINGS)
                }
                .setNegativeButton(R.string.cancel) { dialog, which -> deny() }
                .setOnCancelListener { deny() }.create().show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == RC_SETTINGS && permissionHandler != null) {
            allPermissions?.let {
                Permissions.check(this, it.toTypedArray(), null, options, permissionHandler)
            }
        }
        // super, because overridden method will make the handler null, and we don't want that.
        super.finish()
    }

    override fun finish() {
        permissionHandler = null
        super.finish()
    }

    private fun deny() {
        val permissionHandler = permissionHandler
        finish()
        permissionHandler?.onDenied(applicationContext, deniedPermissions)
    }

    private fun grant() {
        val permissionHandler = permissionHandler
        finish()
        permissionHandler?.onGranted()
    }

    companion object {
        private const val RC_SETTINGS = 6739
        private const val RC_PERMISSION = 6937
        const val EXTRA_PERMISSIONS = "permissions"
        const val EXTRA_RATIONALE = "rationale"
        const val EXTRA_OPTIONS = "options"
        var permissionHandler: PermissionHandler? = null
    }
}