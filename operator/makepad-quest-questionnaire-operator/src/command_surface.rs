#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct CommandSurfaceItem {
    pub gui_control: &'static str,
    pub cli_equivalent: &'static str,
}

pub const GUI_COMMAND_SURFACE: &[CommandSurfaceItem] = &[
    CommandSurfaceItem {
        gui_control: "Endpoint field",
        cli_equivalent: "--endpoint",
    },
    CommandSurfaceItem {
        gui_control: "Session field",
        cli_equivalent: "--session-id",
    },
    CommandSurfaceItem {
        gui_control: "Participant field",
        cli_equivalent: "--participant-ref",
    },
    CommandSurfaceItem {
        gui_control: "Language field",
        cli_equivalent: "--language-code",
    },
    CommandSurfaceItem {
        gui_control: "Poll button",
        cli_equivalent: "status",
    },
    CommandSurfaceItem {
        gui_control: "Dismiss button",
        cli_equivalent: "dismiss",
    },
    CommandSurfaceItem {
        gui_control: "ADB serial field",
        cli_equivalent: "--serial",
    },
    CommandSurfaceItem {
        gui_control: "Panel APK field",
        cli_equivalent: "--apk",
    },
    CommandSurfaceItem {
        gui_control: "Install Panel button",
        cli_equivalent: "install-panel",
    },
    CommandSurfaceItem {
        gui_control: "Host port field",
        cli_equivalent: "--host-port",
    },
    CommandSurfaceItem {
        gui_control: "Quest port field",
        cli_equivalent: "--device-port",
    },
    CommandSurfaceItem {
        gui_control: "Tools button",
        cli_equivalent: "tooling-status",
    },
    CommandSurfaceItem {
        gui_control: "Devices button",
        cli_equivalent: "devices",
    },
    CommandSurfaceItem {
        gui_control: "Status button",
        cli_equivalent: "device-status",
    },
    CommandSurfaceItem {
        gui_control: "Forward button",
        cli_equivalent: "bridge-forward",
    },
    CommandSurfaceItem {
        gui_control: "Open Block 1 button",
        cli_equivalent: "open-block --block 1",
    },
    CommandSurfaceItem {
        gui_control: "Open Block 2 button",
        cli_equivalent: "open-block --block 2",
    },
    CommandSurfaceItem {
        gui_control: "Open Block 3 button",
        cli_equivalent: "open-block --block 3",
    },
    CommandSurfaceItem {
        gui_control: "Runtime protocol field",
        cli_equivalent: "--protocol-version",
    },
    CommandSurfaceItem {
        gui_control: "Runtime target APK field",
        cli_equivalent: "--apk",
    },
    CommandSurfaceItem {
        gui_control: "Runtime APK SHA-256 field",
        cli_equivalent: "--sha256",
    },
    CommandSurfaceItem {
        gui_control: "Runtime APK report field",
        cli_equivalent: "--out",
    },
    CommandSurfaceItem {
        gui_control: "Runtime Verify APK button",
        cli_equivalent: "verify-target-apk",
    },
    CommandSurfaceItem {
        gui_control: "Runtime Install APK button",
        cli_equivalent: "install-target-apk",
    },
    CommandSurfaceItem {
        gui_control: "Runtime Launch button",
        cli_equivalent: "launch-target-runtime",
    },
    CommandSurfaceItem {
        gui_control: "Runtime kind field",
        cli_equivalent: "--runtime-kind",
    },
    CommandSurfaceItem {
        gui_control: "Runtime package field",
        cli_equivalent: "--runtime-package",
    },
    CommandSurfaceItem {
        gui_control: "Runtime study field",
        cli_equivalent: "--study-id",
    },
    CommandSurfaceItem {
        gui_control: "Runtime condition field",
        cli_equivalent: "--condition-id",
    },
    CommandSurfaceItem {
        gui_control: "Runtime build tag field",
        cli_equivalent: "--runtime-build-tag",
    },
    CommandSurfaceItem {
        gui_control: "Runtime source scene field",
        cli_equivalent: "--source-scene-path",
    },
    CommandSurfaceItem {
        gui_control: "Runtime questionnaire field",
        cli_equivalent: "--questionnaire-id",
    },
    CommandSurfaceItem {
        gui_control: "Runtime stage field",
        cli_equivalent: "--open-stage",
    },
    CommandSurfaceItem {
        gui_control: "Runtime marker field",
        cli_equivalent: "--marker-name",
    },
    CommandSurfaceItem {
        gui_control: "Runtime remote field",
        cli_equivalent: "--remote-relative",
    },
    CommandSurfaceItem {
        gui_control: "Runtime pull out field",
        cli_equivalent: "--out",
    },
    CommandSurfaceItem {
        gui_control: "Runtime Preflight button",
        cli_equivalent: "preflight-runtime",
    },
    CommandSurfaceItem {
        gui_control: "Runtime Start button",
        cli_equivalent: "start-session",
    },
    CommandSurfaceItem {
        gui_control: "Runtime Mark button",
        cli_equivalent: "mark-timing-event",
    },
    CommandSurfaceItem {
        gui_control: "Runtime Open Q button",
        cli_equivalent: "open-questionnaire",
    },
    CommandSurfaceItem {
        gui_control: "Runtime Stop button",
        cli_equivalent: "stop-session",
    },
    CommandSurfaceItem {
        gui_control: "Runtime Pull button",
        cli_equivalent: "pull-session",
    },
    CommandSurfaceItem {
        gui_control: "Runtime Pull Files button",
        cli_equivalent: "pull-target-session",
    },
];

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli;

    #[test]
    fn gui_surface_has_cli_equivalents_in_help_text() {
        let help = cli::run(vec!["help".to_string()]).unwrap();
        for item in GUI_COMMAND_SURFACE {
            let command = item
                .cli_equivalent
                .split_whitespace()
                .next()
                .unwrap_or(item.cli_equivalent);
            assert!(
                help.contains(command),
                "{} should be covered by CLI help entry `{}`",
                item.gui_control,
                item.cli_equivalent
            );
        }
    }
}
