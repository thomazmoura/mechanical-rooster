using RelentlessBadger.Api.Models;

namespace RelentlessBadger.Api.Contracts;

public record GoogleLoginRequest(string IdToken);

public record LoginResponse(string Token, string Email, string? Name, SettingsDto Settings);

public record SettingsDto(
    int InitialDelayMinutes,
    int RepeatIntervalMinutes,
    int MediumWaitMinutes,
    int LongWaitMinutes)
{
    public static SettingsDto From(User user) =>
        new(user.InitialDelayMinutes, user.RepeatIntervalMinutes, user.MediumWaitMinutes, user.LongWaitMinutes);
}

public record CreateTaskRequest(string Title, DateTime? FirstWarningAt = null);

public record TaskDto(
    Guid Id,
    string Title,
    DateTime CreatedAt,
    DateTime? CompletedAt,
    int InitialDelayMinutes,
    int RepeatIntervalMinutes,
    DateTime? FirstWarningAt)
{
    public static TaskDto From(TaskItem task) => new(
        task.Id, task.Title, task.CreatedAt, task.CompletedAt,
        task.InitialDelayMinutes, task.RepeatIntervalMinutes, task.FirstWarningAt);
}
