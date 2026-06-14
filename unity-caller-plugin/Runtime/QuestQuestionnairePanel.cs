using System;
using System.Text;
using UnityEngine;

namespace MesmerPrism.QuestQuestionnairePanel
{
    [Serializable]
    public sealed class QuestQuestionnaireRequest
    {
        public string SessionId;
        public string StudyId;
        public string QuestionnaireId;
        public string OpenStage;
        public string[] ScreenSequence;
        public int ConditionNumber = -1;
        public string ParticipantRef;
        public string CallerPackageName;
        public string CallerAppVersion;
    }

    public static class QuestQuestionnairePanel
    {
        private const string BridgeClass =
            "io.github.mesmerprism.questquestionnaire.unity.UnityQuestionnaireBridge";

        public static string Launch(
            QuestQuestionnaireRequest request,
            string resultAuthority,
            string callbackReceiverClassName,
            bool debugAutoSubmit = false)
        {
            if (request == null)
            {
                throw new ArgumentNullException(nameof(request));
            }
            if (string.IsNullOrWhiteSpace(resultAuthority))
            {
                throw new ArgumentException("Result authority is required.", nameof(resultAuthority));
            }
            if (string.IsNullOrWhiteSpace(callbackReceiverClassName))
            {
                throw new ArgumentException("Callback receiver class is required.", nameof(callbackReceiverClassName));
            }

#if UNITY_ANDROID && !UNITY_EDITOR
            using (var unityPlayer = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
            using (var activity = unityPlayer.GetStatic<AndroidJavaObject>("currentActivity"))
            using (var bridge = new AndroidJavaClass(BridgeClass))
            {
                return bridge.CallStatic<string>(
                    "launch",
                    activity,
                    resultAuthority,
                    callbackReceiverClassName,
                    request.SessionId,
                    request.StudyId,
                    request.QuestionnaireId,
                    request.OpenStage,
                    ToJsonArray(request.ScreenSequence),
                    request.ConditionNumber,
                    request.ParticipantRef,
                    request.CallerPackageName,
                    request.CallerAppVersion,
                    debugAutoSubmit);
            }
#else
            return "{\"status\":\"unsupported_platform\"}";
#endif
        }

        private static string ToJsonArray(string[] values)
        {
            if (values == null || values.Length == 0)
            {
                throw new ArgumentException("Screen sequence must not be empty.", nameof(values));
            }

            var builder = new StringBuilder("[");
            for (var index = 0; index < values.Length; index++)
            {
                if (index > 0)
                {
                    builder.Append(',');
                }
                builder.Append('"').Append(EscapeJsonString(values[index])).Append('"');
            }
            builder.Append(']');
            return builder.ToString();
        }

        private static string EscapeJsonString(string value)
        {
            if (string.IsNullOrWhiteSpace(value))
            {
                throw new ArgumentException("Screen sequence contains a blank stage.");
            }

            return value
                .Replace("\\", "\\\\")
                .Replace("\"", "\\\"")
                .Replace("\n", "\\n")
                .Replace("\r", "\\r")
                .Replace("\t", "\\t");
        }
    }

    public static class BrbQuestionnaire
    {
        public const string QuestionnaireId = "brb-questionnaire-v1";

        public static readonly string[] InitialSequence =
        {
            "language_select",
            "demographics",
            "prior_experience"
        };

        public static readonly string[] ConditionOnePostSequence =
        {
            "post_condition:pictographic",
            "post_condition:presence_questionnaire",
            "post_condition:lost_opportunity"
        };

        public static readonly string[] ConditionTwoPostSequence =
        {
            "post_condition:pictographic",
            "post_condition:presence_questionnaire"
        };

        public static readonly string[] FinalSequence =
        {
            "final:end_confirmation",
            "final:extra_presses_prompt",
            "complete:export_summary"
        };
    }

    public static class GenericQuestionnaire
    {
        public const string QuestionnaireId = "generic-questionnaire-v1";

        public static readonly string[] DemoSequence =
        {
            "generic:intro",
            "generic:rating",
            "generic:comment",
            "generic:complete"
        };
    }
}
