; ============================================
; Extended Keybind Test Script for Modern Chat
; ============================================
;
; This script remaps keys for testing F13-F24 and Mouse 4/5
; Run this script, then test in RuneLite
;
; To stop: Right-click the green "H" icon in system tray → Exit
;
; ============================================

#SingleInstance Force
#NoEnv
SendMode Input

; Show a tooltip when script starts
ToolTip, Extended Keybind Test Script Active!`n`nCtrl+F1-F12 → F13-F24`nCtrl+4 → Mouse 4`nCtrl+5 → Mouse 5`n`nRight-click tray icon to exit
SetTimer, RemoveToolTip, 5000
return

RemoveToolTip:
    ToolTip
    SetTimer, RemoveToolTip, Off
return

; ============================================
; F13-F24 Mappings (Ctrl + F1-F12)
; ============================================

^F1::Send {F13}
^F2::Send {F14}
^F3::Send {F15}
^F4::Send {F16}
^F5::Send {F17}
^F6::Send {F18}
^F7::Send {F19}
^F8::Send {F20}
^F9::Send {F21}
^F10::Send {F22}
^F11::Send {F23}
^F12::Send {F24}

; ============================================
; Mouse Button Mappings
; ============================================

; Ctrl+4 sends Mouse Button 4 (XButton1 / Back)
^4::Send {XButton1}

; Ctrl+5 sends Mouse Button 5 (XButton2 / Forward)
^5::Send {XButton2}

; ============================================
; Alternative: Numpad mappings (uncomment if preferred)
; ============================================

; Numpad1::Send {F13}
; Numpad2::Send {F14}
; Numpad3::Send {F15}
; Numpad4::Send {F16}
; Numpad5::Send {F17}
; Numpad6::Send {F18}
; Numpad7::Send {F19}
; Numpad8::Send {F20}
; Numpad9::Send {F21}
; Numpad0::Send {F22}
; NumpadDot::Send {F23}
; NumpadEnter::Send {F24}

; ============================================
; Hotkey to show reminder of mappings
; ============================================

^F24::  ; Ctrl+F24 (press Ctrl+Ctrl+F12) shows help
    MsgBox, 0, Extended Keybind Test Mappings,
    (
    F13-F24 Mappings:
    Ctrl+F1  → F13
    Ctrl+F2  → F14
    Ctrl+F3  → F15
    Ctrl+F4  → F16
    Ctrl+F5  → F17
    Ctrl+F6  → F18
    Ctrl+F7  → F19
    Ctrl+F8  → F20
    Ctrl+F9  → F21
    Ctrl+F10 → F22
    Ctrl+F11 → F23
    Ctrl+F12 → F24

    Mouse Button Mappings:
    Ctrl+4 → Mouse Button 4
    Ctrl+5 → Mouse Button 5
    )
return
