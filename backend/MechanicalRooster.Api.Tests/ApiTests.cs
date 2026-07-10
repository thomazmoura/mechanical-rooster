using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using MechanicalRooster.Api.Contracts;

namespace MechanicalRooster.Api.Tests;

public class ApiTests : IClassFixture<TestAppFactory>
{
    private readonly TestAppFactory _factory;

    public ApiTests(TestAppFactory factory) => _factory = factory;

    private async Task<HttpClient> LoginAsync(string sub = "sub-1", string email = "user@example.com")
    {
        var client = _factory.CreateClient();
        var response = await client.PostAsJsonAsync("/auth/google",
            new GoogleLoginRequest($"{sub}|{email}|Test User"));
        response.EnsureSuccessStatusCode();
        var login = await response.Content.ReadFromJsonAsync<LoginResponse>();
        client.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", login!.Token);
        return client;
    }

    [Fact]
    public async Task Endpoints_require_authentication()
    {
        var client = _factory.CreateClient();
        Assert.Equal(HttpStatusCode.Unauthorized, (await client.GetAsync("/tasks")).StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, (await client.GetAsync("/me/settings")).StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, (await client.GetAsync("/tasks/titles")).StatusCode);
    }

    [Fact]
    public async Task Invalid_google_token_is_rejected()
    {
        var client = _factory.CreateClient();
        var response = await client.PostAsJsonAsync("/auth/google", new GoogleLoginRequest("garbage"));
        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task Login_twice_reuses_the_same_user()
    {
        var client1 = await LoginAsync(sub: "repeat-sub");
        var created = await (await client1.PostAsJsonAsync("/tasks", new CreateTaskRequest("persisted task")))
            .Content.ReadFromJsonAsync<TaskDto>();
        Assert.NotNull(created);

        var client2 = await LoginAsync(sub: "repeat-sub");
        var tasks = await client2.GetFromJsonAsync<List<TaskDto>>("/tasks?status=open");
        Assert.Contains(tasks!, t => t.Id == created!.Id);
    }

    [Fact]
    public async Task Task_lifecycle_create_list_complete()
    {
        var client = await LoginAsync(sub: "lifecycle-sub");

        var createResponse = await client.PostAsJsonAsync("/tasks", new CreateTaskRequest("  take out trash  "));
        Assert.Equal(HttpStatusCode.Created, createResponse.StatusCode);
        var task = await createResponse.Content.ReadFromJsonAsync<TaskDto>();
        Assert.Equal("take out trash", task!.Title);
        Assert.Null(task.CompletedAt);

        var open = await client.GetFromJsonAsync<List<TaskDto>>("/tasks?status=open");
        Assert.Contains(open!, t => t.Id == task.Id);

        var completeResponse = await client.PostAsync($"/tasks/{task.Id}/complete", null);
        completeResponse.EnsureSuccessStatusCode();
        var completed = await completeResponse.Content.ReadFromJsonAsync<TaskDto>();
        Assert.NotNull(completed!.CompletedAt);

        open = await client.GetFromJsonAsync<List<TaskDto>>("/tasks?status=open");
        Assert.DoesNotContain(open!, t => t.Id == task.Id);

        var done = await client.GetFromJsonAsync<List<TaskDto>>("/tasks?status=done");
        Assert.Contains(done!, t => t.Id == task.Id);
    }

    [Fact]
    public async Task First_warning_time_round_trips_and_defaults_to_null()
    {
        var client = await LoginAsync(sub: "first-warning-sub");

        var warningAt = new DateTime(2026, 7, 12, 9, 0, 0, DateTimeKind.Utc);
        var scheduled = await (await client.PostAsJsonAsync("/tasks",
                new CreateTaskRequest("scheduled task", warningAt)))
            .Content.ReadFromJsonAsync<TaskDto>();
        Assert.Equal(warningAt, scheduled!.FirstWarningAt!.Value.ToUniversalTime());

        var plain = await (await client.PostAsJsonAsync("/tasks", new CreateTaskRequest("plain task")))
            .Content.ReadFromJsonAsync<TaskDto>();
        Assert.Null(plain!.FirstWarningAt);
    }

    [Fact]
    public async Task Empty_title_is_rejected()
    {
        var client = await LoginAsync(sub: "empty-title-sub");
        var response = await client.PostAsJsonAsync("/tasks", new CreateTaskRequest("   "));
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    [Fact]
    public async Task Settings_round_trip_and_snapshot_into_new_tasks()
    {
        var client = await LoginAsync(sub: "settings-sub");

        var defaults = await client.GetFromJsonAsync<SettingsDto>("/me/settings");
        Assert.Equal(60, defaults!.InitialDelayMinutes);
        Assert.Equal(15, defaults.RepeatIntervalMinutes);
        Assert.Equal(60, defaults.MediumWaitMinutes);
        Assert.Equal(240, defaults.LongWaitMinutes);

        var putResponse = await client.PutAsJsonAsync("/me/settings", new SettingsDto(30, 5, 90, 300));
        putResponse.EnsureSuccessStatusCode();

        var updated = await client.GetFromJsonAsync<SettingsDto>("/me/settings");
        Assert.Equal(30, updated!.InitialDelayMinutes);
        Assert.Equal(5, updated.RepeatIntervalMinutes);
        Assert.Equal(90, updated.MediumWaitMinutes);
        Assert.Equal(300, updated.LongWaitMinutes);

        var task = await (await client.PostAsJsonAsync("/tasks", new CreateTaskRequest("water plants")))
            .Content.ReadFromJsonAsync<TaskDto>();
        Assert.Equal(30, task!.InitialDelayMinutes);
        Assert.Equal(5, task.RepeatIntervalMinutes);

        var invalid = await client.PutAsJsonAsync("/me/settings", new SettingsDto(0, 5, 90, 300));
        Assert.Equal(HttpStatusCode.BadRequest, invalid.StatusCode);

        var invalidWait = await client.PutAsJsonAsync("/me/settings", new SettingsDto(30, 5, 0, 300));
        Assert.Equal(HttpStatusCode.BadRequest, invalidWait.StatusCode);
    }

    [Fact]
    public async Task Titles_are_distinct_and_ordered_by_frequency()
    {
        var client = await LoginAsync(sub: "titles-sub");

        foreach (var title in new[] { "take out trash", "walk dog", "take out trash" })
        {
            var response = await client.PostAsJsonAsync("/tasks", new CreateTaskRequest(title));
            response.EnsureSuccessStatusCode();
        }

        var titles = await client.GetFromJsonAsync<List<string>>("/tasks/titles");
        Assert.Equal(2, titles!.Count);
        Assert.Equal("take out trash", titles[0]);
        Assert.Equal("walk dog", titles[1]);
    }

    [Fact]
    public async Task Users_cannot_see_or_touch_each_others_tasks()
    {
        var alice = await LoginAsync(sub: "alice-sub", email: "alice@example.com");
        var bob = await LoginAsync(sub: "bob-sub", email: "bob@example.com");

        var aliceTask = await (await alice.PostAsJsonAsync("/tasks", new CreateTaskRequest("alice secret")))
            .Content.ReadFromJsonAsync<TaskDto>();

        var bobTasks = await bob.GetFromJsonAsync<List<TaskDto>>("/tasks?status=all");
        Assert.DoesNotContain(bobTasks!, t => t.Id == aliceTask!.Id);

        Assert.Equal(HttpStatusCode.NotFound,
            (await bob.PostAsync($"/tasks/{aliceTask!.Id}/complete", null)).StatusCode);
        Assert.Equal(HttpStatusCode.NotFound,
            (await bob.DeleteAsync($"/tasks/{aliceTask.Id}")).StatusCode);
    }

    [Fact]
    public async Task Delete_removes_task()
    {
        var client = await LoginAsync(sub: "delete-sub");
        var task = await (await client.PostAsJsonAsync("/tasks", new CreateTaskRequest("to be deleted")))
            .Content.ReadFromJsonAsync<TaskDto>();

        var deleteResponse = await client.DeleteAsync($"/tasks/{task!.Id}");
        Assert.Equal(HttpStatusCode.NoContent, deleteResponse.StatusCode);

        var all = await client.GetFromJsonAsync<List<TaskDto>>("/tasks?status=all");
        Assert.DoesNotContain(all!, t => t.Id == task.Id);
    }
}
