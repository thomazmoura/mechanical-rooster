namespace MechanicalRooster.Api.Models;

public class TaskItem
{
    public Guid Id { get; set; }
    public Guid UserId { get; set; }
    public required string Title { get; set; }
    public DateTime CreatedAt { get; set; }
    public DateTime? CompletedAt { get; set; }

    // Snapshotted from the user's defaults at creation time so that changing
    // the defaults later does not affect tasks already being nagged about.
    public int InitialDelayMinutes { get; set; }
    public int RepeatIntervalMinutes { get; set; }

    public User? User { get; set; }
}
