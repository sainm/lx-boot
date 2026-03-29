export type ApiResponse<T> = {
  code: string;
  message: string;
  data: T;
};

export type PageResponse<T> = {
  list: T[];
  page: number;
  size: number;
  total: number;
};
