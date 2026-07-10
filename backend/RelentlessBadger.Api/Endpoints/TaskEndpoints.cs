using System.Security.Claims;
using RelentlessBadger.Api.Contracts;
using RelentlessBadger.Api.Data;
using RelentlessBadger.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace RelentlessBadger.Api.Endpoints;

public static class TaskEndpoints
{
    public static void MapTaskEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/tasks").RequireAuthorization();

        group.MapGet("/", async (string? status, ClaimsPrincipal principal, AppDbContext db) =>
        {
            var userId = principal.GetUserId();
            var query = db.Tasks.Where(t => t.UserId == userId);
            query = status switch
            {
                "open" or null => query.Where(t => t.CompletedAt == null),
                "done" => query.Where(t => t.CompletedAt != null),
                "all" => query,
                _ => query.Where(t => t.CompletedAt == null),
            };

            var tasks = await query.OrderByDescending(t => t.CreatedAt).ToListAsync();
            return Results.Ok(tasks.Select(TaskDto.From));
        });

        group.MapPost("/", async (CreateTaskRequest request, ClaimsPrincipal principal, AppDbContext db) =>
        {
            var title = request.Title?.Trim();
            if (string.IsNullOrEmpty(title))
            {
                return Results.BadRequest(new { error = "Title is required." });
            }

            var user = await principal.GetUserAsync(db);
            if (user is null)
            {
                return Results.Unauthorized();
            }

            var task = new TaskItem
            {
                Id = Guid.NewGuid(),
                UserId = user.Id,
                Title = title,
                CreatedAt = DateTime.UtcNow,
                InitialDelayMinutes = user.InitialDelayMinutes,
                RepeatIntervalMinutes = user.RepeatIntervalMinutes,
                FirstWarningAt = request.FirstWarningAt?.ToUniversalTime(),
            };
            db.Tasks.Add(task);
            await db.SaveChangesAsync();

            return Results.Created($"/tasks/{task.Id}", TaskDto.From(task));
        });

        group.MapPost("/{id:guid}/complete", async (Guid id, ClaimsPrincipal principal, AppDbContext db) =>
        {
            var userId = principal.GetUserId();
            var task = await db.Tasks.SingleOrDefaultAsync(t => t.Id == id && t.UserId == userId);
            if (task is null)
            {
                return Results.NotFound();
            }

            task.CompletedAt ??= DateTime.UtcNow;
            await db.SaveChangesAsync();
            return Results.Ok(TaskDto.From(task));
        });

        group.MapDelete("/{id:guid}", async (Guid id, ClaimsPrincipal principal, AppDbContext db) =>
        {
            var userId = principal.GetUserId();
            var deleted = await db.Tasks
                .Where(t => t.Id == id && t.UserId == userId)
                .ExecuteDeleteAsync();
            return deleted == 0 ? Results.NotFound() : Results.NoContent();
        });

        // Distinct historical titles, most frequent first (recency breaks ties).
        // Feeds the client-side fuzzy autocomplete.
        group.MapGet("/titles", async (ClaimsPrincipal principal, AppDbContext db) =>
        {
            var userId = principal.GetUserId();
            var titles = await db.Tasks
                .Where(t => t.UserId == userId)
                .GroupBy(t => t.Title)
                .Select(g => new { Title = g.Key, Count = g.Count(), Latest = g.Max(t => t.CreatedAt) })
                .OrderByDescending(g => g.Count)
                .ThenByDescending(g => g.Latest)
                .Take(500)
                .Select(g => g.Title)
                .ToListAsync();
            return Results.Ok(titles);
        });
    }
}
