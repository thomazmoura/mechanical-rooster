using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace RelentlessBadger.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddRecurrenceToTasks : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "RecurDaysOfWeek",
                table: "Tasks",
                type: "integer",
                nullable: true);

            migrationBuilder.AddColumn<int>(
                name: "RecurEveryN",
                table: "Tasks",
                type: "integer",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "RecurUnit",
                table: "Tasks",
                type: "text",
                nullable: true);

            migrationBuilder.AddColumn<Guid>(
                name: "SeriesId",
                table: "Tasks",
                type: "uuid",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "RecurDaysOfWeek",
                table: "Tasks");

            migrationBuilder.DropColumn(
                name: "RecurEveryN",
                table: "Tasks");

            migrationBuilder.DropColumn(
                name: "RecurUnit",
                table: "Tasks");

            migrationBuilder.DropColumn(
                name: "SeriesId",
                table: "Tasks");
        }
    }
}
