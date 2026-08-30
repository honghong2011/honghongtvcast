package com.hh2011.cast.tv

import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.hh2011.cast.tv.service.CastService
import com.hh2011.cast.tv.util.PreferencesHelper

/**
 * 投屏接收端设置页
 *
 * UI 由 activity_settings.xml + item_setting_*.xml 定义结构，
 * 代码只负责 inflate + 填充数据 + 绑定回调。
 *
 * 参考 honghongTV 的设置页风格，按投屏接收端需求简化：
 * - 投屏设置：DLNA 开关、设备名称修改、开机自启动
 * - 播放设置：优先硬件解码、屏幕常亮
 */
class SettingsActivity : FragmentActivity() {

    private lateinit var settingsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settingsContainer = findViewById(R.id.settingsContainer)

        // 返回按钮：保存提示并结束
        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener {
            toast(getString(R.string.settings_saved))
            setResult(RESULT_OK)
            finish()
        }

        buildSettings()
    }

    /** 构建设置项列表 */
    private fun buildSettings() {
        // 分类：投屏设置
        addCategory("投屏设置")
        addSwitchItem(
            getString(R.string.settings_dlna_switch),
            PreferencesHelper.isDlnaEnabled(this)
        ) { checked ->
            PreferencesHelper.setDlnaEnabled(this, checked)
            // 根据开关状态启停投屏服务
            if (checked) {
                CastService.start(this)
            } else {
                CastService.stop(this)
            }
            toast(if (checked) "已开启 DLNA 投屏" else "已关闭 DLNA 投屏")
        }
        // 设备名称修改（点击弹出编辑对话框）
        addNavigableButton(
            getString(R.string.settings_device_name),
            PreferencesHelper.getDeviceName(this)
        ) {
            showDeviceNameEditDialog()
        }
        addSwitchItem(
            getString(R.string.settings_boot_auto_start),
            PreferencesHelper.isBootAutoStartEnabled(this)
        ) { checked ->
            PreferencesHelper.setBootAutoStartEnabled(this, checked)
            toast(if (checked) "已开启开机自启" else "已关闭开机自启")
        }

        // 分类：播放设置
        addCategory("播放设置")
        addSwitchItem(
            getString(R.string.settings_hw_decode),
            PreferencesHelper.isHardwareDecodingEnabled(this)
        ) { checked ->
            PreferencesHelper.setHardwareDecodingEnabled(this, checked)
            toast(if (checked) "已开启优先硬件解码" else "已开启优先软件解码")
        }
        addSwitchItem(
            getString(R.string.settings_keep_screen_on),
            PreferencesHelper.isKeepScreenOnEnabled(this)
        ) { checked ->
            PreferencesHelper.setKeepScreenOnEnabled(this, checked)
            toast(if (checked) "已开启屏幕常亮" else "已关闭屏幕常亮")
        }
    }

    /** 添加分类标题 */
    private fun addCategory(text: String) {
        val tv = layoutInflater
            .inflate(R.layout.item_setting_category, settingsContainer, false) as TextView
        tv.text = text
        settingsContainer.addView(tv)
    }

    /** 添加开关项：整行可获焦，点击整行切换开关 */
    private fun addSwitchItem(title: String, isChecked: Boolean, onChange: (Boolean) -> Unit) {
        val view = layoutInflater
            .inflate(R.layout.item_setting_switch, settingsContainer, false)
        view.findViewById<TextView>(R.id.tvTitle).text = title
        val switch = view.findViewById<SwitchMaterial>(R.id.switchToggle)
        switch.isChecked = isChecked
        switch.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        // 整行点击也切换开关
        view.setOnClickListener { switch.isChecked = !switch.isChecked }
        settingsContainer.addView(view)
    }

    /** 添加可导航按钮项：标题 + 摘要 + 箭头，点击触发回调 */
    private fun addNavigableButton(title: String, summary: String, onClick: () -> Unit) {
        val view = layoutInflater
            .inflate(R.layout.item_setting_button, settingsContainer, false)
        view.findViewById<TextView>(R.id.tvTitle).text = title
        view.findViewById<TextView>(R.id.tvSummary).text = summary
        view.setOnClickListener { onClick() }
        settingsContainer.addView(view)
    }

    /**
     * 设备名称编辑对话框
     *
     * TV 端用系统 IME 输入文字，遥控器可操作。
     */
    private fun showDeviceNameEditDialog() {
        val currentName = PreferencesHelper.getDeviceName(this)
        val editText = EditText(this).apply {
            setText(currentName)
            setSelection(currentName.length)
            hint = "请输入设备名称"
        }
        val container = LinearLayout(this).apply {
            setPadding(50, 30, 50, 10)
            addView(editText)
        }
        AlertDialog.Builder(this)
            .setTitle("修改设备名称")
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    PreferencesHelper.setDeviceName(this, newName)
                    toast("设备名称已修改为：$newName")
                    // 如果 DLNA 服务正在运行，重启以应用新设备名称
                    if (PreferencesHelper.isDlnaEnabled(this)) {
                        CastService.stop(this)
                        CastService.start(this)
                    }
                    recreate()
                } else {
                    toast("设备名称不能为空")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 显示短 Toast（项目暂无 ToastHelper，直接用系统 Toast） */
    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}
