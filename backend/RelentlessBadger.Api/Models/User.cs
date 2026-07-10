namespace RelentlessBadger.Api.Models;

public class User
{
    public Guid Id { get; set; }
    public required string GoogleSub { get; set; }
    public required string Email { get; set; }
    public string? Name { get; set; }
    public int InitialDelayMinutes { get; set; } = 60;
    public int RepeatIntervalMinutes { get; set; } = 15;
    public int MediumWaitMinutes { get; set; } = 60;
    public int LongWaitMinutes { get; set; } = 240;

    public List<TaskItem> Tasks { get; set; } = [];
}
