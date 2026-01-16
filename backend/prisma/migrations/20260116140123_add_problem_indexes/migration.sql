-- CreateIndex
CREATE INDEX `problem_details_likes_idx` ON `problem_details`(`likes`);

-- CreateIndex
CREATE INDEX `problems_difficulty_idx` ON `problems`(`difficulty`);

-- CreateIndex
CREATE INDEX `problems_slug_idx` ON `problems`(`slug`);

-- CreateIndex
CREATE INDEX `problems_title_idx` ON `problems`(`title`);
