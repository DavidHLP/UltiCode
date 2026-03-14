import { Controller, Get, Query, UseGuards } from '@nestjs/common';
import { SearchService } from './search.service';
import { SearchQueryDto, SearchResponse } from './dto/search-query.dto';
import { AuthGuard } from '../auth/auth.guard';

@Controller('search')
export class SearchController {
  constructor(private readonly searchService: SearchService) {}

  @Get()
  @UseGuards(AuthGuard)
  async search(@Query() dto: SearchQueryDto): Promise<SearchResponse> {
    return this.searchService.search(dto);
  }
}
